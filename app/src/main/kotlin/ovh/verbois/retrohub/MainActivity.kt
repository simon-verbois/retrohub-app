package ovh.verbois.retrohub

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ovh.verbois.retrohub.account.AuthState
import ovh.verbois.retrohub.account.QrCameraException
import ovh.verbois.retrohub.emulation.InterfaceOrientation
import ovh.verbois.retrohub.ui.RetroHubRoot
import ovh.verbois.retrohub.ui.UiController

class MainActivity : ComponentActivity() {
    private val guide by lazy {
        ovh.verbois.retrohub.game.GuideButton(
            lifecycleScope,
            onPress = { controller?.let { it.menuOpen = !it.menuOpen } },
            onHold = { controller?.returnHome() },
        )
    }
    private var controller: UiController? = null
    private var qrResult: CompletableDeferred<String?>? = null
    private val scanLauncher = registerForActivityResult(ScanContract()) { result -> qrResult?.complete(result.contents) }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchQrCamera() else qrResult?.completeExceptionally(QrCameraException())
    }

    private fun launchQrCamera() {
        scanLauncher.launch(
            ScanOptions().setCaptureActivity(PairingScanActivity::class.java)
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt(getString(R.string.qr_camera_prompt))
                .setBeepEnabled(false).setBarcodeImageEnabled(false).setOrientationLocked(false),
        )
    }

    private suspend fun scanQrCode(): String? {
        check(qrResult == null)
        val result = CompletableDeferred<String?>()
        qrResult = result
        try {
            cameraPermission.launch(android.Manifest.permission.CAMERA)
            return result.await()
        } finally {
            qrResult = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        immersive()
        val container = (application as RetroHubApp).container
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.settings.settings.map { it.interfaceOrientation }.distinctUntilChanged().collect { orientation ->
                    val requested = when (orientation) {
                        InterfaceOrientation.SYSTEM -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        InterfaceOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                        InterfaceOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                    if (requestedOrientation != requested) requestedOrientation = requested
                }
            }
        }
        (application as RetroHubApp).platform.qrScanner = { scanQrCode() }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    controller?.back()
                }
            },
        )
        setContent {
            RetroHubRoot(container, onExit = { finish() }) {
                controller = it
                if (intent.getBooleanExtra(EXTRA_RETURN_HOME, false)) {
                    it.returnHome()
                    intent.removeExtra(EXTRA_RETURN_HOME)
                }
            }
        }
        // Notifications (téléchargements, conflits) : demandées une fois la configuration faite.
        lifecycleScope.launch {
            container.settings.settings.first { it.setupDone }
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
    }

    /**
     * Les boutons « système » de la manette (B, Y, LB, RB, Start, Select) agissent même quand
     * aucun élément n’a encore le focus (mode tactile) ; A et la croix restent à Compose.
     */
    // Faux positif de lint : super.dispatchKeyEvent est l’API publique d’Activity.
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val active = controller
        if (event.keyCode in setOf(KeyEvent.KEYCODE_BUTTON_MODE, KeyEvent.KEYCODE_HOME) &&
            active?.container?.account?.auth?.value is AuthState.SignedIn && active.container.settings.settings.value.setupDone
        ) {
            guide.event(event.action == KeyEvent.ACTION_DOWN, event.isCanceled)
            return true
        }
        controller?.keyCapture?.let { capture ->
            if (KeyEvent.isGamepadButton(event.keyCode) ||
                event.keyCode in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT
            ) {
                if (event.action == KeyEvent.ACTION_UP) capture(event.keyCode)
                return true
            }
        }
        val button =
            when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_B -> UiController.Button.B
                KeyEvent.KEYCODE_BUTTON_Y -> UiController.Button.Y
                KeyEvent.KEYCODE_BUTTON_L1 -> UiController.Button.LB
                KeyEvent.KEYCODE_BUTTON_R1 -> UiController.Button.RB
                KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU -> UiController.Button.START
                KeyEvent.KEYCODE_BUTTON_SELECT -> UiController.Button.SELECT
                else -> null
            }
        val ui = controller
        if (button != null && ui != null && ui.container.account.auth.value is AuthState.SignedIn &&
            ui.container.settings.settings.value.setupDone
        ) {
            if (event.action == KeyEvent.ACTION_DOWN && window.decorView.isInTouchMode) window.decorView.requestFocusFromTouch()
            // Focused controls and modal windows get the first chance to handle shortcuts.
            if (super.dispatchKeyEvent(event)) return true
            if (event.action == KeyEvent.ACTION_UP && event.repeatCount == 0) {
                ui.onButton(button)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_RETURN_HOME, false)) {
            val ui = controller
            if (ui != null) ui.returnHome() else this.intent.putExtra(EXTRA_RETURN_HOME, true)
        }
    }

    override fun onPause() {
        guide.cancel()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        immersive()
        // Retour au premier plan : un 304 coûte peu, la bibliothèque reste à jour.
        val container = (application as RetroHubApp).container
        lifecycleScope.launch {
            container.start()
            if (container.account.auth.value is AuthState.SignedIn) container.platform.scheduleSync()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    override fun onDestroy() {
        (application as RetroHubApp).platform.qrScanner = null
        qrResult?.complete(null)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_RETURN_HOME = "return_home"
    }

    private fun immersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
