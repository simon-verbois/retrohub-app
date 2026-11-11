package ovh.verbois.retrohub.game

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import ovh.verbois.retrohub.core.AppClock
import ovh.verbois.retrohub.core.sha256
import ovh.verbois.retrohub.data.LocalSave
import ovh.verbois.retrohub.data.PlaySessionEntity
import ovh.verbois.retrohub.emulation.DUALSHOCK
import ovh.verbois.retrohub.emulation.EmulatorEngine
import ovh.verbois.retrohub.sync.SaveStore
import ovh.verbois.retrohub.sync.SaveSync
import ovh.verbois.retrohub.sync.SessionStore
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
    private val saves: SaveStore,
    private val saveSync: SaveSync,
    private val sessions: SessionStore,
    private val scheduleSync: () -> Unit,
    private val scope: CoroutineScope,
    private val clock: AppClock = AppClock.System,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val state = MutableStateFlow(GameUiState())
    val ui: StateFlow<GameUiState> = state.asStateFlow()
    private val lock = Mutex()
    private val finishLock = Mutex()
    private var startedAt: Instant? = null
    private var activeMillis = 0L
    private var resumedAt: Instant? = null
    private var autosave: Job? = null
    private var finished = false

    /** À la première image : on recharge la dernière partie, puis la sauvegarde périodique démarre. */
    suspend fun onFirstFrame() = lock.withLock {
        if (state.value.started || finished) return@withLock
        startedAt = clock.now()
        resumedAt = startedAt
        if (plan.analog) {
            val ports = if (plan.multiplayer) 0..3 else 0..0
            ports.forEach { engine.setControllerType(it, DUALSHOCK) }
        }
        applyCheats(plan.cheats)
        plan.statePath?.let { path ->
            val file = path.toPath()
            val data = withContext(io) { if (fs.exists(file)) fs.read(file) { readByteArray() } else null }
            if (data != null) {
                val loaded = engine.loadState(data)
                if (loaded && plan.notice == null) flash("Reprise de votre dernière partie")
            }
        }
        state.update { it.copy(started = true, message = it.message ?: plan.notice) }
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

    suspend fun saveState(slot: Int): Boolean = withContext(io) {
        lock.withLock {
            val data = engine.saveState()
            if (data == null || data.isEmpty()) {
                flash("Cet émulateur ne peut pas sauvegarder à cet instant. Réessayez dans un moment.")
                return@withLock false
            }
            write("state", slot, data)
            if (slot > 0) flash("Partie sauvegardée dans l’emplacement $slot")
            refreshSlots()
            true
        }
    }

    suspend fun loadState(slot: Int): Boolean = withContext(io) {
        lock.withLock {
            val file = saves.file(plan.playGameId, "state", slot)
            val save = saves.find(plan.playGameId, "state", slot)
            if (!fs.exists(file)) return@withLock false
            if (save != null && save.core.isNotEmpty() && save.core != plan.core) {
                flash("Cette sauvegarde vient d’un autre émulateur (${save.core}) et ne peut pas être chargée ici.")
                return@withLock false
            }
            if (save != null && save.romSha.isNotEmpty() && !save.romSha.equals(plan.romSha, true)) {
                flash("Cette sauvegarde vient d’une autre version du jeu. Mettez le jeu à jour pour la retrouver.")
                return@withLock false
            }
            val ok = engine.loadState(fs.read(file) { readByteArray() })
            flash(if (ok) "Emplacement $slot chargé" else "Cette sauvegarde ne se charge pas avec cette version du jeu.")
            ok
        }
    }

    /** SRAM et état automatique (emplacement 0), seulement s’ils ont changé. */
    suspend fun autoSave() = withContext(io) {
        lock.withLock {
            if (!state.value.started || finished) return@withLock
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
        val digest = sha256(data)
        if (fs.exists(target) && saves.find(plan.playGameId, kind, slot)?.localSha == digest) return
        val temporary = target.parent!! / (target.name + ".tmp")
        fs.write(temporary) { write(data) }
        fs.atomicMove(temporary, target)
        saveSync.recordLocal(plan.playGameId, plan.console, kind, slot, plan.core, plan.romSha, digest)
    }

    private suspend fun refreshSlots() {
        val existing = saves.forGame(plan.playGameId).filter { it.kind == "state" }.associateBy { it.slot }
        state.update { it.copy(slots = (0..9).map { slot -> SlotInfo(slot, existing[slot]) }) }
    }

    /** Fin de partie : dernière sauvegarde, session notée pour le serveur, synchro demandée. */
    suspend fun finish() = withContext(NonCancellable) {
        finishLock.withLock {
            if (finished) return@withLock
            lock.withLock { /* Wait for any initial restore before taking the final snapshot. */ }
            autosave?.cancelAndJoin()
            resumedAt?.let { activeMillis += (clock.now() - it).inWholeMilliseconds }
            resumedAt = null
            if (state.value.started) autoSave()
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
            lock.withLock { finished = true }
        }
    }

    private companion object {
        const val MIN_SESSION_SECONDS = 10
    }
}
