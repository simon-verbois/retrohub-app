package ovh.verbois.retrohub.library

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import ovh.verbois.retrohub.api.ApiException
import ovh.verbois.retrohub.api.BiosInfo
import ovh.verbois.retrohub.core.AppClock
import ovh.verbois.retrohub.core.sha256
import ovh.verbois.retrohub.data.AppDatabase
import ovh.verbois.retrohub.data.AppPaths
import ovh.verbois.retrohub.data.LocalRomEntity
import ovh.verbois.retrohub.sync.CorruptedDownloadException
import ovh.verbois.retrohub.sync.FileDownloader
import ovh.verbois.retrohub.sync.RomChangedException
import ovh.verbois.retrohub.sync.unzip

sealed interface DownloadState {
    data class Running(val received: Long, val total: Long) : DownloadState {
        val fraction: Float get() = if (total > 0) (received.toFloat() / total).coerceIn(0f, 1f) else 0f
    }

    data class Failed(val message: String, val retryable: Boolean = false) : DownloadState
}

data class StorageUsage(val roms: Long, val saves: Long, val system: Long, val free: Long) {
    val total: Long get() = roms + saves + system
}

/** Téléchargements des jeux, BIOS et suppression des ROM (les sauvegardes restent). */
class DownloadManager(
    private val downloader: FileDownloader,
    private val fs: FileSystem,
    private val paths: AppPaths,
    private val db: AppDatabase,
    private val library: LibraryRepository,
    private val scope: CoroutineScope,
    private val freeBytes: () -> Long,
    private val clock: AppClock = AppClock.System,
    /** Raison d’attendre avant de télécharger (données mobiles refusées), ou null. */
    private val blocked: () -> String? = { null },
) {
    private val state = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = state.asStateFlow()
    private val jobs = mutableMapOf<String, Job>()

    fun start(game: Game) {
        val key = game.playGameId
        if (jobs[key]?.isActive == true) return
        val reason = blocked()
        state.update {
            it +
                (key to (if (reason != null) DownloadState.Failed(reason, retryable = true) else DownloadState.Running(0, game.remote.rom?.size ?: 0)))
        }
        val background = backgroundLauncher
        if (background != null) {
            background(key, game.title)
        } else {
            jobs[key] = scope.launch { perform(key) }
        }
    }

    /** Lance le téléchargement dans une tâche de fond de la plateforme (notification, appli fermée). */
    var backgroundLauncher: ((playGameId: String, title: String) -> Unit)? = null
    var backgroundCanceller: ((playGameId: String) -> Unit)? = null

    /** Le téléchargement lui-même, appelé ici ou par la tâche de fond ; vrai s’il a réussi. */
    suspend fun perform(playGameId: String, onProgress: (Float) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        val game = library.find(playGameId) ?: return@withContext false.also { state.update { it - playGameId } }
        state.update { it + (playGameId to DownloadState.Running(0, game.remote.rom?.size ?: 0)) }
        try {
            blocked()?.let { throw DownloadWaiting(it) }
            download(game) { fraction -> onProgress(fraction) }
            state.update { it - playGameId }
            true
        } catch (error: kotlinx.coroutines.CancellationException) {
            state.update { it - playGameId }
            throw error
        } catch (error: Exception) {
            state.update {
                it +
                    (
                        playGameId to
                            DownloadState.Failed(
                                message(error),
                                retryable =
                                error is ApiException.Offline || error is ApiException.Maintenance || error is DownloadWaiting,
                            )
                        )
            }
            false
        }
    }

    fun cancel(playGameId: String) {
        jobs.remove(playGameId)?.cancel()
        backgroundCanceller?.invoke(playGameId)
        state.update { it - playGameId }
    }

    /** Supprime les jeux non joués depuis [days] jours (leurs sauvegardes restent). */
    suspend fun cleanup(days: Int, games: List<Game>): Int {
        if (days <= 0) return 0
        val limit = clock.now() - kotlin.time.Duration.parse("${days}d")
        var removed = 0
        for (rom in db.roms().all()) {
            if (state.value.containsKey(rom.playGameId)) continue
            val game = games.firstOrNull { it.playGameId == rom.playGameId }
            val last = game?.lastPlayed ?: ovh.verbois.retrohub.core.parseInstant(rom.downloadedAt)
            if (last != null && last < limit) {
                delete(rom.playGameId)
                removed++
            }
        }
        return removed
    }

    /** Déplace les jeux téléchargés vers le dossier courant (stockage interne ou carte SD). */
    suspend fun moveAll() = withContext(Dispatchers.IO) {
        for (rom in db.roms().all()) {
            val from = rom.path.toPath()
            val target = paths.romDir(rom.playGameId) / from.name
            if (from == target || !fs.exists(from)) continue
            target.parent?.let { fs.createDirectories(it) }
            val temporary = target.parent!! / (target.name + ".moving")
            fs.copy(from, temporary)
            check(fs.sha256(temporary).equals(rom.sha256, true)) { "The copied ROM failed verification." }
            withContext(NonCancellable) {
                fs.atomicMove(temporary, target)
                db.roms().upsert(rom.copy(path = target.toString()))
                fs.delete(from)
            }
        }
    }

    fun dismiss(playGameId: String) = state.update { it - playGameId }

    /** Télécharge et vérifie la ROM ; relit le catalogue une fois si elle a changé entre-temps. */
    suspend fun download(game: Game, onProgress: (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
        var current = game
        repeat(2) { attempt ->
            val rom = current.remote.rom ?: throw ApiException.Http(404, "Ce jeu n’est pas encore installé sur le serveur.")
            val partial = paths.romDir(current.playGameId) / (safeName(rom.filename, rom.extension) + ".part")
            val received = fs.metadataOrNull(partial)?.size ?: 0L
            val needed = (rom.size - received).coerceAtLeast(0)
            if (needed > freeBytes()) throw NotEnoughSpace(needed)
            try {
                val dir = paths.romDir(current.playGameId)
                val target = dir / safeName(rom.filename, rom.extension)
                var lastPublished: kotlin.time.Instant? = null
                downloader.downloadRom(rom, target) { received, total ->
                    blocked()?.let { throw DownloadWaiting(it) }
                    val now = clock.now()
                    if (lastPublished == null || (now - lastPublished!!).inWholeMilliseconds >= 200 || (total > 0 && received >= total)) {
                        lastPublished = now
                        state.update { it + (current.playGameId to DownloadState.Running(received, total)) }
                        if (total > 0) onProgress(received.toFloat() / total)
                    }
                }
                onProgress(1f)
                // Une ancienne version du fichier, sous un autre nom, ne sert plus.
                fs.list(dir).filter { it != target && !it.name.endsWith(".part") }.forEach { fs.delete(it) }
                db.roms().upsert(
                    LocalRomEntity(
                        playGameId = current.playGameId,
                        gameId = current.id,
                        console = current.console,
                        title = current.title,
                        path = target.toString(),
                        sha256 = rom.sha256,
                        size = rom.size,
                        downloadedAt = clock.now().toString(),
                    ),
                )
                current.remote.bios?.let { ensureBios(current.console, it) }
                return@withContext
            } catch (changed: RomChangedException) {
                if (attempt == 1) throw changed
                library.invalidate()
                library.refresh()
                current = library.find(current.id) ?: throw changed
            }
        }
    }

    /** Installe le BIOS de la console dans le dossier système (une seule fois par version). */
    private val biosLock = Mutex()

    suspend fun ensureBios(console: String, bios: BiosInfo) = withContext(Dispatchers.IO) {
        biosLock.withLock {
            val marker = paths.system / ".bios-$console"
            if (fs.exists(marker) && fs.read(marker) { readUtf8() } == bios.sha256) return@withLock
            val archive = paths.system / ".download" / "$console-bios.zip"
            downloader.downloadBios(bios, archive)
            unzip(fs, archive, paths.system)
            fs.delete(archive)
            fs.write(marker) { writeUtf8(bios.sha256) }
        }
    }

    suspend fun biosInstalled(console: String, bios: BiosInfo): Boolean = withContext(Dispatchers.IO) {
        val marker = paths.system / ".bios-$console"
        fs.exists(marker) && fs.read(marker) { readUtf8() } == bios.sha256
    }

    /** Supprime la ROM de l’appareil ; ses sauvegardes restent, ici et sur le serveur. */
    suspend fun delete(playGameId: String) = withContext(Dispatchers.IO) {
        cancel(playGameId)
        val rom = db.roms().find(playGameId)
        val dir = rom?.path?.toPath()?.parent ?: paths.romDir(playGameId)
        if (fs.exists(dir)) fs.deleteRecursively(dir)
        if (rom != null) db.roms().delete(rom)
    }

    suspend fun usage(): StorageUsage = withContext(Dispatchers.IO) {
        StorageUsage(
            size(paths.internalRoms) + (if (paths.roms != paths.internalRoms) size(paths.roms) else 0),
            size(paths.saves) + size(paths.coreSaves),
            size(paths.system) + size(paths.covers),
            freeBytes(),
        )
    }

    private fun size(path: Path): Long = if (!fs.exists(path)) {
        0
    } else {
        fs.listRecursively(path).sumOf { runCatching { fs.metadata(it).size ?: 0L }.getOrDefault(0L) }
    }

    private fun safeName(filename: String, extension: String): String {
        val cleaned = filename.replace(Regex("[\\\\/:*?\"<>|\\u0000]"), "_").trim().trimStart('.')
        return cleaned.ifEmpty { "game.$extension" }
    }

    private fun message(error: Exception): String = when (error) {
        is DownloadWaiting -> error.message!!
        is NotEnoughSpace -> error.message!!
        is ApiException.Offline -> "Connexion perdue. Le téléchargement reprendra là où il s’est arrêté."
        is ApiException -> error.message ?: "Téléchargement impossible."
        is CorruptedDownloadException -> error.message!!
        is RomChangedException -> error.message!!
        else -> "Téléchargement impossible. Réessayez."
    }
}

class NotEnoughSpace(needed: Long) :
    Exception("Pas assez d’espace libre (${ovh.verbois.retrohub.core.formatBytes(needed)} nécessaires). Supprimez un jeu téléchargé.")

private class DownloadWaiting(message: String) : Exception(message)
