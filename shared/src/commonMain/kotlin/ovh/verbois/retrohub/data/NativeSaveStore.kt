package ovh.verbois.retrohub.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import ovh.verbois.retrohub.api.ApiJson
import ovh.verbois.retrohub.core.AppClock
import ovh.verbois.retrohub.core.sha256
import ovh.verbois.retrohub.game.NativeGameSaves
import ovh.verbois.retrohub.sync.SaveStore

/** New directory/card formats are private to an account; legacy consoles retain their Room store. */
class NativeSaveStore(
    private val legacy: SaveStore,
    override val fs: FileSystem,
    private val root: Path,
    private val owner: () -> String,
    private val changes: MutableStateFlow<Long> = MutableStateFlow(0L),
    private val activeGames: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet()),
) : SaveStore {
    private val consoles = MutableStateFlow<Map<String, String>>(emptyMap())
    private val accountRoot: Path get() = root / safe(owner())

    fun boundTo(ownerKey: String): NativeSaveStore = NativeSaveStore(legacy, fs, root, { ownerKey }, changes, activeGames)

    @Serializable
    private data class RuntimeBinding(val console: String, val core: String, val directory: String, val romSha: String, val titleId: String?)

    override fun registerNativeRuntime(gameId: String, console: String, core: String, directory: String, romSha: String, titleId: String?) {
        if (console !in privateConsoles) return
        prepareGame(gameId, console)
        val target = directory(gameId) / "runtime.binding"
        fs.createDirectories(target.parent!!)
        val temporary = target.parent!! / "runtime.binding.tmp"
        fs.write(temporary) { writeUtf8(ApiJson.encodeToString(RuntimeBinding.serializer(), RuntimeBinding(console, core, directory, romSha, titleId))) }
        fs.atomicMove(temporary, target)
    }

    override suspend fun checkpointGame(gameId: String) = withContext(Dispatchers.IO) { boundTo(owner()).recoverRuntime(gameId) }

    override suspend fun checkpointAll() = withContext(Dispatchers.IO) {
        val scoped = boundTo(owner())
        if (fs.exists(scoped.accountRoot)) {
            fs.list(scoped.accountRoot).filter { fs.metadata(it).isDirectory }.forEach { scoped.recoverRuntime(it.name) }
        }
    }

    private suspend fun recoverRuntime(gameId: String) {
        if (isGameActive(gameId)) return
        val bindingFile = directory(gameId) / "runtime.binding"
        if (!fs.exists(bindingFile)) return
        val binding = fs.read(bindingFile) { ApiJson.decodeFromString(RuntimeBinding.serializer(), readUtf8()) }
        prepareGame(gameId, binding.console)
        val runtime = binding.directory.toPath()
        if (!fs.exists(runtime)) return
        val existing = find(gameId, "sram", 0)
        if (existing?.inConflict == true || (existing != null && existing.romSha != binding.romSha)) return
        val marker = runtime / ".restored-archive"
        val applied = if (fs.exists(marker)) fs.read(marker) { readUtf8().substringBefore('\n') } else null
        // A server copy chosen/restored by the player has not yet been installed in the runtime.
        if (existing?.localSha != null && existing.localSha != applied) return
        val generation = changes.value
        val temporary = directory(gameId) / "runtime-checkpoint.tmp"
        try {
            if (!NativeGameSaves.export(fs, runtime, binding.console, gameId, binding.romSha, binding.titleId, temporary, markCheckpoint = false)) return
            if (isGameActive(gameId) || changes.value != generation) return
            val digest = fs.sha256(temporary)
            if (existing?.localSha == digest) return
            fs.atomicMove(temporary, file(gameId, "sram", 0))
            put(
                (existing ?: LocalSave(gameId, "sram", 0, binding.console)).copy(
                    localSha = digest,
                    localPlayedAt = AppClock.System.now().toString(),
                    core = binding.core,
                    romSha = binding.romSha,
                    lastError = null,
                ),
            )
            NativeGameSaves.checkpoint(fs, runtime, binding.console, gameId, binding.romSha, binding.titleId, digest)
        } finally {
            fs.delete(temporary, mustExist = false)
        }
    }

    override fun setGameActive(gameId: String, active: Boolean) {
        if (!isPrivate(gameId)) return
        val key = "${owner()}/$gameId"
        activeGames.update { if (active) it + key else it - key }
        changes.update { it + 1 }
    }

    override fun isGameActive(gameId: String): Boolean = "${owner()}/$gameId" in activeGames.value

    override fun prepareGame(gameId: String, console: String) {
        consoles.update { it + (gameId to console) }
    }

    private fun isPrivate(gameId: String): Boolean = consoles.value[gameId]?.let { it in privateConsoles }
        ?: fs.exists(accountRoot / safe(gameId))

    private fun safe(value: String): String = value.also {
        require(it.isNotEmpty() && it.all { c -> c.isLetterOrDigit() || c in "._:-" } && it != "." && it != "..")
    }

    private fun directory(gameId: String) = accountRoot / safe(gameId)
    private fun metadata(gameId: String, kind: String, slot: Int) = directory(gameId) / "${safe(kind)}-$slot.json"
    private fun read(path: Path): LocalSave? = if (fs.exists(path)) fs.read(path) { ApiJson.decodeFromString(LocalSave.serializer(), readUtf8()) } else null

    override suspend fun find(gameId: String, kind: String, slot: Int): LocalSave? =
        if (isPrivate(gameId)) read(metadata(gameId, kind, slot)) else legacy.find(gameId, kind, slot)

    override suspend fun forGame(gameId: String): List<LocalSave> = if (isPrivate(gameId)) {
        val dir = directory(gameId)
        if (fs.exists(dir)) fs.list(dir).filter { it.name.endsWith(".json") }.mapNotNull(::read) else emptyList()
    } else {
        legacy.forGame(gameId)
    }

    override suspend fun all(): List<LocalSave> {
        val privateSaves = if (fs.exists(accountRoot)) {
            fs.listRecursively(accountRoot).filter { it.name.endsWith(".json") && fs.metadata(it).isRegularFile }.mapNotNull(::read).toList()
        } else {
            emptyList()
        }
        return legacy.all().filterNot { it.console in privateConsoles } + privateSaves
    }

    override suspend fun put(save: LocalSave) {
        prepareGame(save.gameId, save.console)
        if (save.console !in privateConsoles) {
            legacy.put(save)
            return
        }
        val destination = metadata(save.gameId, save.kind, save.slot)
        fs.createDirectories(destination.parent!!)
        val temporary = destination.parent!! / (destination.name + ".${kotlin.uuid.Uuid.random()}.tmp")
        fs.write(temporary) { writeUtf8(ApiJson.encodeToString(LocalSave.serializer(), save)) }
        fs.atomicMove(temporary, destination)
        changes.update { it + 1 }
    }

    override fun file(gameId: String, kind: String, slot: Int): Path =
        if (isPrivate(gameId)) directory(gameId) / "${safe(kind)}-$slot.bin" else legacy.file(gameId, kind, slot)

    override fun conflictFile(gameId: String, kind: String, slot: Int): Path =
        if (isPrivate(gameId)) directory(gameId) / "server-${safe(kind)}-$slot.bin" else legacy.conflictFile(gameId, kind, slot)

    override fun observe(gameId: String) = combine(legacy.observe(gameId), changes) { _, _ -> forGame(gameId) }.flowOn(Dispatchers.IO)

    companion object {
        val privateConsoles = setOf("3ds", "ps2")
    }
}
