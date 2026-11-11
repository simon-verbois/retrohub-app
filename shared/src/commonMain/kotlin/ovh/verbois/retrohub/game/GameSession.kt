package ovh.verbois.retrohub.game

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.FileSystem
import okio.Path.Companion.toPath
import ovh.verbois.retrohub.core.AppClock
import ovh.verbois.retrohub.data.LocalSave
import ovh.verbois.retrohub.data.PlaySessionEntity
import ovh.verbois.retrohub.data.RoomSaveStore
import ovh.verbois.retrohub.data.RoomSessionStore
import ovh.verbois.retrohub.emulation.DUALSHOCK
import ovh.verbois.retrohub.emulation.EmulatorEngine
import ovh.verbois.retrohub.sync.SaveSync
import ovh.verbois.retrohub.sync.file
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class SlotInfo(val slot: Int, val save: LocalSave?) {
    val empty: Boolean get() = save?.localSha == null
}

data class GameUiState(
    val paused: Boolean = false,
    val fastForward: Boolean = false,
    val started: Boolean = false,
    val message: String? = null,
    val slots: List<SlotInfo> = emptyList(),
    val lastSavedAt: Instant? = null,
)

/**
 * Une partie en cours, indépendante de la plateforme : temps de jeu effectif, sauvegarde
 * automatique (SRAM et état 0, comme le web), états manuels 1 à 9, et fin de partie.
 */
class GameSession(
    val plan: LaunchPlan,
    private val engine: EmulatorEngine,
    private val fs: FileSystem,
    private val saves: RoomSaveStore,
    private val saveSync: SaveSync,
    private val sessions: RoomSessionStore,
    private val scheduleSync: () -> Unit,
    private val scope: CoroutineScope,
    private val clock: AppClock = AppClock.System,
) {
    private val state = MutableStateFlow(GameUiState())
    val ui: StateFlow<GameUiState> = state.asStateFlow()
    private val lock = Mutex()
    private var startedAt: Instant? = null
    private var activeMillis = 0L
    private var resumedAt: Instant? = null
    private var autosave: Job? = null
    private var finished = false

    /** À la première image : on recharge la dernière partie, puis la sauvegarde périodique démarre. */
    fun onFirstFrame() {
        if (state.value.started) return
        startedAt = clock.now()
        resumedAt = startedAt
        state.update { it.copy(started = true, message = plan.notice) }
        if (plan.analog) {
            val ports = if (plan.multiplayer) 0..3 else 0..0
            ports.forEach { engine.setControllerType(it, DUALSHOCK) }
        }
        applyCheats(plan.cheats)
        plan.statePath?.let { path ->
            val file = path.toPath()
            if (fs.exists(file)) {
                val loaded = engine.loadState(fs.read(file) { readByteArray() })
                if (loaded && plan.notice == null) flash("Reprise de votre dernière partie")
            }
        }
        scope.launch { refreshSlots() }
        autosave =
            scope.launch {
                while (isActive) {
                    delay(plan.autoSaveSeconds.coerceAtLeast(15) * 1000L)
                    if (!state.value.paused) autoSave()
                }
            }
    }

    fun pause() {
        if (state.value.paused) return
        resumedAt?.let { activeMillis += (clock.now() - it).inWholeMilliseconds }
        resumedAt = null
        engine.pause()
        state.update { it.copy(paused = true) }
        scope.launch { refreshSlots() }
    }

    fun resume() {
        if (!state.value.paused) return
        resumedAt = clock.now()
        engine.resume()
        state.update { it.copy(paused = false) }
    }

    fun toggleFastForward() {
        val enabled = !state.value.fastForward
        engine.setFastForward(if (enabled) plan.fastForwardSpeed else 1)
        if (plan.audio && plan.muteFastForward) engine.setAudio(!enabled)
        state.update { it.copy(fastForward = enabled) }
    }

    fun flash(message: String?) = state.update { it.copy(message = message) }

    private var cheatCount = 0

    /** Codes de triche : chaque code actif est envoyé au cœur ; les anciens sont désactivés. */
    fun applyCheats(cheats: List<Cheat>) {
        val active = cheats.filter { it.enabled && it.code.isNotBlank() }
        active.forEachIndexed { index, cheat -> engine.setCheat(index, true, cheat.code.trim()) }
        for (index in active.size until cheatCount) engine.setCheat(index, false, "")
        cheatCount = active.size
    }

    suspend fun saveState(slot: Int): Boolean = lock.withLock {
        val data = engine.saveState()
        if (data == null || data.isEmpty()) {
            flash("Cet émulateur ne peut pas sauvegarder à cet instant. Réessayez dans un moment.")
            return false
        }
        write("state", slot, data)
        if (slot > 0) flash("Partie sauvegardée dans l’emplacement $slot")
        refreshSlots()
        true
    }

    suspend fun loadState(slot: Int): Boolean {
        val file = saves.file(plan.playGameId, "state", slot)
        val save = saves.find(plan.playGameId, "state", slot)
        if (!fs.exists(file)) return false
        if (save != null && save.core.isNotEmpty() && save.core != plan.core) {
            flash("Cette sauvegarde vient d’un autre émulateur (${save.core}) et ne peut pas être chargée ici.")
            return false
        }
        val ok = engine.loadState(fs.read(file) { readByteArray() })
        flash(if (ok) "Emplacement $slot chargé" else "Cette sauvegarde ne se charge pas avec cette version du jeu.")
        return ok
    }

    /** SRAM et état automatique (emplacement 0), seulement s’ils ont changé. */
    suspend fun autoSave() {
        lock.withLock {
            val code = plan.gameCode
            val userDir = plan.dolphinUserDir
            if (code != null && userDir != null) {
                // GameCube : la « SRAM » échangée est le ZIP des fichiers .gci de ce jeu.
                val target = saves.file(plan.playGameId, "sram", 0)
                target.parent?.let { fs.createDirectories(it) }
                if (GameCubeSaves.export(fs, userDir.toPath(), code, target)) {
                    saveSync.recordLocal(plan.playGameId, plan.console, "sram", 0, plan.core, plan.romSha)
                }
            } else {
                // Un jeu sans pile renvoie une SRAM vide ou pleine de zéros : rien à garder.
                engine.saveSram()?.takeIf { data -> data.any { it != 0.toByte() } }?.let { write("sram", 0, it) }
            }
            engine.saveState()?.takeIf { it.isNotEmpty() }?.let { write("state", 0, it) }
            state.update { it.copy(lastSavedAt = clock.now()) }
        }
    }

    private suspend fun write(kind: String, slot: Int, data: ByteArray) {
        val target = saves.file(plan.playGameId, kind, slot)
        target.parent?.let { fs.createDirectories(it) }
        if (fs.exists(target) && fs.read(target) { readByteArray() }.contentEquals(data)) return
        val temporary = target.parent!! / (target.name + ".tmp")
        fs.write(temporary) { write(data) }
        fs.atomicMove(temporary, target)
        saveSync.recordLocal(plan.playGameId, plan.console, kind, slot, plan.core, plan.romSha)
    }

    private suspend fun refreshSlots() {
        val existing = saves.forGame(plan.playGameId).filter { it.kind == "state" }.associateBy { it.slot }
        state.update { it.copy(slots = (0..9).map { slot -> SlotInfo(slot, existing[slot]) }) }
    }

    /** Fin de partie : dernière sauvegarde, session notée pour le serveur, synchro demandée. */
    suspend fun finish() {
        if (finished) return
        finished = true
        autosave?.cancel()
        if (state.value.started) autoSave()
        resumedAt?.let { activeMillis += (clock.now() - it).inWholeMilliseconds }
        val start = startedAt
        val seconds = activeMillis / 1000
        if (start != null && seconds >= MIN_SESSION_SECONDS) {
            sessions.add(
                PlaySessionEntity(
                    clientId = Uuid.random().toString(),
                    gameId = plan.playGameId,
                    startedAt = start.toString(),
                    endedAt = clock.now().toString(),
                    playedSeconds = seconds,
                ),
            )
        }
        scheduleSync()
    }

    private companion object {
        const val MIN_SESSION_SECONDS = 10
    }
}
