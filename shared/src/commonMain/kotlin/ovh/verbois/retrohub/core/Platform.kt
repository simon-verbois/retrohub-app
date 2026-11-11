package ovh.verbois.retrohub.core

import kotlinx.coroutines.flow.StateFlow
import okio.FileSystem
import okio.Path
import ovh.verbois.retrohub.game.LaunchPlan

/** Le jeton de l’appareil, chiffré par la plateforme (Keystore Android, Trousseau iOS). */
interface TokenVault {
    fun read(): String?

    fun write(token: String)

    fun clear()
}

enum class DeviceClass { MODEST, CAPABLE, POWERFUL }

/** Ce que chaque plateforme fournit au code partagé. */
interface Platform {
    /** « android » ou « ios », envoyé au serveur avec le nom de l’appareil. */
    val name: String
    val appVersion: String
    val defaultDeviceName: String
    val fs: FileSystem

    /** Stockage interne de l’application : ROM, BIOS, sauvegardes. */
    val filesDir: Path
    val tokenVault: TokenVault
    val online: StateFlow<Boolean>
    val gamepadConnected: StateFlow<Boolean>
    val deviceClass: DeviceClass
    val canScanQrCode: Boolean get() = false

    /** Read a QR code locally. Null means that the user dismissed the camera. */
    suspend fun scanQrCode(): String? = null

    /** Le système demande de supprimer les animations (accessibilité). */
    val reducedMotion: Boolean

    /** Lance la synchronisation en tâche de fond (dès que le réseau est là). */
    fun scheduleSync(delaySeconds: Long = 0)

    /** Cœurs libretro présents dans l’application. */
    fun availableCores(): Set<String>

    fun launchGame(plan: LaunchPlan)

    fun freeBytes(): Long

    /** Le réseau actuel est facturé à l’usage (données mobiles). */
    fun meteredNetwork(): Boolean

    fun openUrl(url: String)

    fun vibrate(milliseconds: Long = 12)

    /** Vibration d’un jeu (rumble), intensité de 0 à 1. */
    fun rumble(strength: Float)

    /** Synchronisation périodique en arrière-plan ; 0 heure = désactivée. */
    fun schedulePeriodicSync(hours: Int)

    /** Notification système (conflit de sauvegarde, téléchargement terminé). */
    fun notify(id: Int, title: String, text: String)

    /** Téléchargement qui continue application fermée, avec sa notification de progression. */
    fun startBackgroundDownload(playGameId: String, title: String)

    /** Dossier des jeux sur la carte SD, s’il y en a une. */
    fun sdCardRoot(): Path?

    /** Petit son de navigation dans les menus. */
    fun playClick()

    fun batteryLow(): Boolean

    /** Partage d’un rapport (journal de l’application et état) pour signaler un problème. */
    fun shareLogs(summary: String)
}
