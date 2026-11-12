package ovh.verbois.retrohub.emulation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Boutons de la manette libretro (RetroPad), désignés par leur position. */
enum class PadKey {
    UP,
    DOWN,
    LEFT,
    RIGHT,
    A,
    B,
    X,
    Y,
    L,
    R,
    L2,
    R2,
    L3,
    R3,
    START,
    SELECT,
}

enum class Stick { LEFT, RIGHT }

/** Zone normalisée (0..1) de l’écran où le jeu est dessiné. */
data class NormRect(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height

    companion object {
        val Full = NormRect(0f, 0f, 1f, 1f)
    }
}

/**
 * Le moteur d’émulation d’une plateforme (LibretroDroid sur Android, frontend libretro natif sur
 * iOS). Tout le reste du jeu (menus, commandes tactiles, sauvegardes) est partagé.
 */
interface EmulatorEngine {
    /** Nombre d’images rendues depuis le lancement (sert aussi aux tests de bout en bout). */
    val frames: StateFlow<Long>

    /** Erreurs fatales du cœur, en message affichable. */
    val errors: Flow<String>

    fun pause()

    fun resume()

    /** Capture any core-specific restart checkpoint before restoring the player's save. */
    suspend fun prepareReset() = Unit

    suspend fun saveState(): ByteArray?

    suspend fun loadState(data: ByteArray): Boolean

    suspend fun saveSram(): ByteArray?

    suspend fun reset()

    fun setFastForward(speed: Int)

    fun setAudio(enabled: Boolean)

    fun key(key: PadKey, pressed: Boolean, port: Int = 0)

    fun stick(stick: Stick, x: Float, y: Float, port: Int = 0)

    /** Toucher sur l’image du jeu (écran tactile de la DS), en pixels de la vue ; null = relâché. */
    fun touch(x: Float?, y: Float?)

    fun setViewport(rect: NormRect)

    fun setRotation(rotation: GameRotation, aspect: Float) = Unit

    fun setOptions(options: Map<String, String>)

    /** Options déclarées par le cœur en cours (clé, valeur, libellé et valeurs possibles). */
    fun variables(): List<CoreVariable>

    fun setCheat(index: Int, enabled: Boolean, code: String)

    /** Type de manette d’un port (libretro), par exemple la DualShock de la PlayStation. */
    fun setControllerType(port: Int, type: Int)

    /** Vibrations demandées par le jeu (0 à 1). */
    val rumble: Flow<Float>
}

/** Une option d’un cœur libretro, lue dans le cœur en cours. */
data class CoreVariable(val key: String, val value: String, val label: String, val values: List<String>) {
    companion object {
        /** Format libretro : « Libellé; valeur1|valeur2|valeur3 ». */
        fun parse(key: String, value: String, description: String?): CoreVariable {
            val label = description?.substringBefore(';')?.trim().orEmpty().ifEmpty { key }
            val values = description?.substringAfter(';', "")?.split('|')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
            return CoreVariable(key, value, label, values)
        }
    }
}

/** Manette analogique DualShock (RETRO_DEVICE_SUBCLASS(RETRO_DEVICE_ANALOG, 1)). */
const val DUALSHOCK = (2 shl 8) or 5
