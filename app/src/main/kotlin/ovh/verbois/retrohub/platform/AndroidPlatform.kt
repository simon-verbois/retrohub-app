package ovh.verbois.retrohub.platform

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.StatFs
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.InputDevice
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import ovh.verbois.retrohub.BuildConfig
import ovh.verbois.retrohub.api.ApiJson
import ovh.verbois.retrohub.core.BatteryLevel
import ovh.verbois.retrohub.core.ControllerBattery
import ovh.verbois.retrohub.core.DeviceClass
import ovh.verbois.retrohub.core.Platform
import ovh.verbois.retrohub.core.PowerStatus
import ovh.verbois.retrohub.core.TokenVault
import ovh.verbois.retrohub.game.GameActivity
import ovh.verbois.retrohub.game.LaunchPlan
import ovh.verbois.retrohub.work.DownloadWorker
import ovh.verbois.retrohub.work.SyncWorker
import java.io.File

class AndroidPlatform(private val context: Context) : Platform {
    internal var qrScanner: (suspend () -> String?)? = null
    override val canScanQrCode: Boolean get() = qrScanner != null &&
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)
    override suspend fun scanQrCode(): String? = qrScanner?.invoke()
    override val name = "android"
    override val appVersion: String = BuildConfig.VERSION_NAME
    override val defaultDeviceName: String =
        (
            if (Build.MODEL.startsWith(Build.MANUFACTURER, ignoreCase = true)) {
                Build.MODEL
            } else {
                "${Build.MANUFACTURER.replaceFirstChar {
                    it.uppercase()
                }} ${Build.MODEL}"
            }
            ).take(80)
    override val fs: FileSystem = FileSystem.SYSTEM
    override val filesDir: Path = context.filesDir.toOkioPath()
    override val tokenVault: TokenVault = KeystoreTokenVault(context)

    private val onlineState = MutableStateFlow(isOnline())
    override val online: StateFlow<Boolean> = onlineState
    private val gamepadState = MutableStateFlow(hasGamepad())
    override val gamepadConnected: StateFlow<Boolean> = gamepadState

    override val deviceClass: DeviceClass by lazy {
        val memory = ActivityManager.MemoryInfo().also { (context.getSystemService(ActivityManager::class.java)).getMemoryInfo(it) }
        val gigabytes = memory.totalMem / (1024.0 * 1024 * 1024)
        val cores = Runtime.getRuntime().availableProcessors()
        when {
            gigabytes >= 7.5 && cores >= 8 -> DeviceClass.POWERFUL
            gigabytes >= 3.5 -> DeviceClass.CAPABLE
            else -> DeviceClass.MODEST
        }
    }

    override val reducedMotion: Boolean
        get() = android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) ==
            0f

    init {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        connectivity.registerNetworkCallback(
            NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
            // On suit l’ensemble des réseaux utilisables : juste après onLost, activeNetwork peut
            // encore désigner le réseau perdu, ce qui laisserait l’application croire qu’elle est en ligne.
            object : ConnectivityManager.NetworkCallback() {
                private val networks = java.util.concurrent.ConcurrentHashMap.newKeySet<Network>()

                override fun onAvailable(network: Network) {
                    networks += network
                    onlineState.value = true
                }

                override fun onLost(network: Network) {
                    networks -= network
                    onlineState.value = networks.isNotEmpty()
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        networks += network
                    } else {
                        networks -=
                            network
                    }
                    onlineState.value = networks.isNotEmpty()
                }
            },
        )
        context.getSystemService(InputManager::class.java).registerInputDeviceListener(
            object : InputManager.InputDeviceListener {
                override fun onInputDeviceAdded(deviceId: Int) {
                    gamepadState.value = hasGamepad()
                }

                override fun onInputDeviceRemoved(deviceId: Int) {
                    gamepadState.value = hasGamepad()
                }

                override fun onInputDeviceChanged(deviceId: Int) {
                    gamepadState.value = hasGamepad()
                }
            },
            null,
        )
    }

    private fun isOnline(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Vrai seulement pour une vraie manette (boutons de jeu et sticks), pas un clavier. */
    private fun hasGamepad(): Boolean = InputDevice.getDeviceIds().any { id ->
        val device = InputDevice.getDevice(id) ?: return@any false
        val sources = device.sources
        !device.isVirtual &&
            (
                sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                    sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
                )
    }

    override fun scheduleSync(delaySeconds: Long) {
        SyncWorker.enqueue(context, delaySeconds)
    }

    override fun availableCores(): Set<String> {
        val dir = File(context.applicationInfo.nativeLibraryDir)
        return dir.list().orEmpty().mapNotNull { CORE_FILE.matchEntire(it)?.groupValues?.get(1) }.toSet()
    }

    fun corePath(core: String): String = File(context.applicationInfo.nativeLibraryDir, "lib${core}_libretro_android.so").absolutePath

    override fun launchGame(plan: LaunchPlan) {
        context.startActivity(
            Intent(context, GameActivity::class.java)
                .putExtra(GameActivity.EXTRA_PLAN, ApiJson.encodeToString(LaunchPlan.serializer(), plan))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    override fun meteredNetwork(): Boolean = context.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered

    override fun freeBytes(): Long = StatFs(context.filesDir.absolutePath).availableBytes

    override fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun vibrate(milliseconds: Long) {
        val vibrator =
            if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
        if (vibrator?.hasVibrator() == true) {
            vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, 80))
        }
    }

    override fun rumble(strength: Float) {
        val vibrator = vibrator() ?: return
        if (strength <= 0.01f) {
            vibrator.cancel()
            return
        }
        if (vibrator.hasVibrator()) vibrator.vibrate(VibrationEffect.createOneShot(80, (strength * 255).toInt().coerceIn(1, 255)))
    }

    override fun schedulePeriodicSync(hours: Int) = SyncWorker.schedulePeriodic(context, hours)

    override fun notify(id: Int, title: String, text: String) = Notifications.show(context, id, title, text, Notifications.CHANNEL_SYNC)

    override fun startBackgroundDownload(playGameId: String, title: String) = DownloadWorker.enqueue(context, playGameId, title)

    fun cancelBackgroundDownload(playGameId: String) = DownloadWorker.cancel(context, playGameId)

    override fun sdCardRoot(): Path? = context.getExternalFilesDirs(null).drop(1).firstOrNull {
        it != null &&
            android.os.Environment.getExternalStorageState(it) == android.os.Environment.MEDIA_MOUNTED
    }?.toOkioPath()

    override fun playClick() {
        context.getSystemService(android.media.AudioManager::class.java).playSoundEffect(android.media.AudioManager.FX_KEY_CLICK, 0.4f)
    }

    override fun batteryLow(): Boolean {
        val battery = context.getSystemService(android.os.BatteryManager::class.java)
        val level = battery.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return level in 0..20 && !battery.isCharging
    }

    override fun powerStatus(): PowerStatus {
        val battery = context.getSystemService(android.os.BatteryManager::class.java)
        val phone = battery.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it in 0..100 }?.let { BatteryLevel(it, battery.isCharging) }
        val controllers = if (Build.VERSION.SDK_INT >= 31) {
            InputDevice.getDeviceIds().toList().mapNotNull { id ->
                val device = InputDevice.getDevice(id) ?: return@mapNotNull null
                if (device.isVirtual || !device.isExternal ||
                    (!device.supportsSource(InputDevice.SOURCE_GAMEPAD) && !device.supportsSource(InputDevice.SOURCE_JOYSTICK))
                ) {
                    return@mapNotNull null
                }
                val state = runCatching { device.batteryState }.getOrNull() ?: return@mapNotNull null
                val capacity = state.capacity
                if (!state.isPresent || !capacity.isFinite() || capacity !in 0f..1f) return@mapNotNull null
                ControllerBattery(
                    device.name,
                    BatteryLevel(
                        kotlin.math.round(capacity * 100).toInt(),
                        state.status == android.hardware.BatteryState.STATUS_CHARGING,
                    ),
                )
            }
        } else {
            emptyList()
        }
        return PowerStatus(phone, controllers)
    }

    override fun shareLogs(summary: String) {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "retrohub-rapport.txt")
        val log =
            runCatching {
                ProcessBuilder(
                    "logcat",
                    "-d",
                    "-t",
                    "3000",
                    "--pid=${android.os.Process.myPid()}",
                ).redirectErrorStream(true).start().inputStream.bufferedReader().readText()
            }.getOrDefault("Journal indisponible.")
        file.writeText(summary + "\n\n--- Journal de l’application ---\n" + log)
        val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".reports", file)
        val send =
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, "Rapport RetroHub ${BuildConfig.VERSION_NAME}")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Partager le rapport").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun vibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    private companion object {
        val CORE_FILE = Regex("lib(.+)_libretro_android\\.so")
    }
}
