package ovh.verbois.retrohub.sync

import okio.FileSystem
import okio.Path
import ovh.verbois.retrohub.api.ApiException
import ovh.verbois.retrohub.api.LibraryGame
import ovh.verbois.retrohub.api.LibraryManifest
import ovh.verbois.retrohub.api.RemoteSave
import ovh.verbois.retrohub.api.RetroHubApi
import ovh.verbois.retrohub.core.AppClock
import ovh.verbois.retrohub.core.sha256
import ovh.verbois.retrohub.data.LocalSave

/** Où sont rangées les sauvegardes et comment les retrouver. Implémenté sur Room, simulé en test. */
interface SaveStore {
    val fs: FileSystem

    fun prepareGame(gameId: String, console: String) = Unit

    fun registerNativeRuntime(gameId: String, console: String, core: String, directory: String, romSha: String, titleId: String?) = Unit

    suspend fun checkpointGame(gameId: String) = Unit

    suspend fun checkpointAll() = Unit

    fun setGameActive(gameId: String, active: Boolean) = Unit

    fun isGameActive(gameId: String): Boolean = false

    fun observe(gameId: String): kotlinx.coroutines.flow.Flow<List<LocalSave>> = kotlinx.coroutines.flow.flow { emit(forGame(gameId)) }

    suspend fun find(gameId: String, kind: String, slot: Int): LocalSave?

    suspend fun forGame(gameId: String): List<LocalSave>

    suspend fun all(): List<LocalSave>

    suspend fun put(save: LocalSave)

    /** Copie locale de l’emplacement (ce que le cœur charge). */
    fun file(gameId: String, kind: String, slot: Int): Path

    /** Version du serveur gardée à côté pendant un conflit. */
    fun conflictFile(gameId: String, kind: String, slot: Int): Path
}

fun SaveStore.file(save: LocalSave): Path = file(save.gameId, save.kind, save.slot)

fun SaveStore.conflictFile(save: LocalSave): Path = conflictFile(save.gameId, save.kind, save.slot)

sealed interface PushResult {
    data object Uploaded : PushResult

    data object Conflict : PushResult

    /** Refus définitif pour cette copie (trop grosse, ROM changée…) : elle reste sur l’appareil. */
    data class Kept(val reason: String) : PushResult
}

/**
 * Synchronisation des sauvegardes (docs/native-api.md, « Synchronisation des sauvegardes ») :
 * révision de base par emplacement, envoi avec If-Match, conflit sans jamais rien écraser,
 * changement de save_epoch traité comme une restauration du serveur.
 */
class SaveSync(private val api: RetroHubApi, private val store: SaveStore, private val clock: AppClock = AppClock.System) {
    private val fs get() = store.fs

    /**
     * Avant de lancer un jeu : récupère les révisions plus récentes que la base, ou détecte
     * un conflit si la copie locale a aussi changé. Renvoie les emplacements en conflit.
     */
    suspend fun pull(game: LibraryGame): List<LocalSave> {
        store.prepareGame(game.playGameId, game.console)
        checkpoint { store.checkpointGame(game.playGameId) }
        if (store.isGameActive(game.playGameId)) return store.forGame(game.playGameId).filter { it.inConflict }
        val conflicts = mutableListOf<LocalSave>()
        for (remote in game.saves) {
            val local = store.find(game.playGameId, remote.kind, remote.slot)
            val result = pullOne(game, remote, local)
            if (result?.inConflict == true) conflicts += result
        }
        return conflicts +
            store.forGame(game.playGameId).filter { it.inConflict && conflicts.none { c -> c.kind == it.kind && c.slot == it.slot } }
    }

    private suspend fun pullOne(game: LibraryGame, remote: RemoteSave, local: LocalSave?): LocalSave? {
        if (local != null && remote.revision <= local.baseRevision && !local.inConflict) return local
        if (local != null && local.inConflict && local.conflictRevision == remote.revision) return local
        return when {
            local == null || !local.changedLocally -> adopt(game.console, remote, local)

            // Même contenu des deux côtés : pas de conflit, la base avance simplement.
            local.localSha.equals(remote.sha256, ignoreCase = true) -> {
                local.copy(baseRevision = remote.revision, baseSha = remote.sha256, conflictRevision = null).also { store.put(it) }
            }

            else -> keepConflict(local, remote)
        }
    }

    /** Remplace la copie locale (inchangée) par la révision du serveur. */
    private suspend fun adopt(console: String, remote: RemoteSave, local: LocalSave?): LocalSave {
        val target = store.file(remote.gameId.ifEmpty { local!!.gameId }, remote.kind, remote.slot)
        target.parent?.let { fs.createDirectories(it) }
        val downloaded = api.readSave(remote.gameId.ifEmpty { local!!.gameId }, remote.kind, remote.slot, remote.revision, fs, target)
        val save =
            (local ?: LocalSave(gameId = remote.gameId, kind = remote.kind, slot = remote.slot, console = console)).copy(
                baseRevision = downloaded.revision.takeIf { it > 0 } ?: remote.revision,
                baseSha = downloaded.sha256,
                localSha = downloaded.sha256,
                localPlayedAt = remote.playedAt,
                core = remote.core,
                coreBuild = remote.coreBuild,
                romSha = remote.romSha256,
                conflictRevision = null,
                conflictSha = null,
                conflictPlayedAt = null,
                conflictDevice = null,
                conflictCore = null,
                lastError = null,
            )
        store.put(save)
        return save
    }

    /** Garde la copie locale, télécharge celle du serveur à côté, et attend le choix du joueur. */
    private suspend fun keepConflict(local: LocalSave, remote: RemoteSave): LocalSave {
        val target = store.conflictFile(local)
        target.parent?.let { fs.createDirectories(it) }
        val downloaded = api.readSave(local.gameId, local.kind, local.slot, remote.revision, fs, target)
        val save =
            local.copy(
                conflictRevision = remote.revision,
                conflictSha = downloaded.sha256,
                conflictPlayedAt = remote.playedAt,
                conflictDevice = remote.device?.name ?: WEB_DEVICE,
                conflictCore = remote.core,
                conflictCoreBuild = remote.coreBuild,
                conflictRomSha = remote.romSha256,
                lastError = null,
            )
        store.put(save)
        return save
    }

    /** Enregistre une sauvegarde faite sur l’appareil (après une partie, hors ligne compris). */
    suspend fun recordLocal(
        gameId: String,
        console: String,
        kind: String,
        slot: Int,
        core: String,
        romSha: String,
        digest: String? = null,
        coreBuild: String? = null,
    ): LocalSave? {
        val path = store.file(gameId, kind, slot)
        if (!fs.exists(path) || (fs.metadata(path).size ?: 0L) == 0L) return store.find(gameId, kind, slot)
        val digest = digest ?: fs.sha256(path)
        val existing = store.find(gameId, kind, slot)
        if (existing != null && existing.localSha == digest && existing.core == core && existing.romSha == romSha &&
            existing.coreBuild == coreBuild
        ) {
            return existing
        }
        val save =
            (existing ?: LocalSave(gameId = gameId, kind = kind, slot = slot, console = console)).copy(
                localSha = digest,
                localPlayedAt = clock.now().toString(),
                core = core,
                coreBuild = coreBuild,
                romSha = romSha,
                lastError = null,
            )
        store.put(save)
        return save
    }

    /** Envoie une copie modifiée localement ; un conflit est préparé pour le joueur. */
    suspend fun push(save: LocalSave, saveEpoch: String): PushResult {
        if (!save.pendingUpload) return PushResult.Uploaded
        val file = store.file(save)
        if (!fs.exists(file)) return PushResult.Kept("missing")
        return try {
            val uploaded =
                api.putSave(
                    RetroHubApi.SaveUpload(
                        gameId = save.gameId,
                        kind = save.kind,
                        slot = save.slot,
                        baseRevision = save.baseRevision,
                        saveEpoch = saveEpoch,
                        romSha256 = save.romSha,
                        core = save.core,
                        coreBuild = save.coreBuild,
                        playedAt = save.localPlayedAt,
                    ),
                    fs,
                    file,
                )
            // La copie a pu changer pendant l’envoi : la base devient ce qui a été envoyé.
            val current = store.find(save.gameId, save.kind, save.slot) ?: save
            store.put(current.copy(baseRevision = uploaded.revision, baseSha = uploaded.sha256, lastError = null))
            PushResult.Uploaded
        } catch (conflict: ApiException.SaveConflict) {
            onConflict(save, conflict.currentRevision)
        } catch (error: ApiException.Http) {
            val reason =
                when (error.status) {
                    413 -> "too_large"
                    409 -> "rom_changed"
                    422 -> "refused"
                    404 -> "not_in_library"
                    else -> throw error
                }
            store.put(save.copy(lastError = reason))
            PushResult.Kept(reason)
        }
    }

    private suspend fun onConflict(save: LocalSave, currentRevision: Int): PushResult {
        val remote = api.saveVersions(save.gameId, save.kind, save.slot).firstOrNull { it.revision == currentRevision }
        if (remote != null && remote.sha256.equals(save.localSha, ignoreCase = true)) {
            // Même contenu déjà sur le serveur (envoyé depuis ailleurs) : la base avance, rien à choisir.
            store.put(save.copy(baseRevision = currentRevision, baseSha = remote.sha256))
            return PushResult.Uploaded
        }
        val server =
            remote ?: RemoteSave(gameId = save.gameId, kind = save.kind, slot = save.slot, revision = currentRevision, sha256 = "")
        keepConflict(save, server)
        return PushResult.Conflict
    }

    /** Le joueur garde sa copie : elle est renvoyée par-dessus la révision du serveur. */
    suspend fun keepLocal(save: LocalSave, saveEpoch: String): PushResult {
        val revision = save.conflictRevision ?: return push(save, saveEpoch)
        val cleared = save.copy(baseRevision = revision, baseSha = save.conflictSha, conflictRevision = null)
        val conflictCopy = store.conflictFile(save)
        store.put(cleared.clearConflict())
        if (fs.exists(conflictCopy)) fs.delete(conflictCopy)
        return push(cleared.clearConflict(), saveEpoch)
    }

    /**
     * Restaurer une ancienne version : elle devient la copie locale puis une nouvelle révision du
     * serveur (l’historique garde toutes les versions, rien n’est perdu).
     */
    suspend fun restore(gameId: String, console: String, version: RemoteSave, saveEpoch: String): PushResult {
        store.prepareGame(gameId, console)
        val target = store.file(gameId, version.kind, version.slot)
        target.parent?.let { fs.createDirectories(it) }
        val head = api.saveVersions(gameId, version.kind, version.slot).maxOfOrNull { it.revision } ?: 0
        api.readSave(gameId, version.kind, version.slot, version.revision, fs, target)
        val digest = fs.sha256(target)
        val current = store.find(gameId, version.kind, version.slot) ?: LocalSave(gameId = gameId, kind = version.kind, slot = version.slot, console = console)
        val save =
            current.copy(
                baseRevision = head,
                baseSha = null,
                localSha = digest,
                localPlayedAt = clock.now().toString(),
                core = version.core.ifEmpty { current.core },
                coreBuild = version.coreBuild,
                romSha = version.romSha256.ifEmpty { current.romSha },
                conflictRevision = null,
                lastError = null,
            )
        store.put(save)
        return push(save, saveEpoch)
    }

    /** Le joueur garde la version du serveur : elle remplace la copie locale. */
    suspend fun keepServer(save: LocalSave) {
        val revision = save.conflictRevision ?: return
        val conflictCopy = store.conflictFile(save)
        if (!fs.exists(conflictCopy)) return
        fs.atomicMove(conflictCopy, store.file(save))
        store.put(
            save
                .copy(
                    baseRevision = revision,
                    baseSha = save.conflictSha,
                    localSha = save.conflictSha,
                    localPlayedAt = save.conflictPlayedAt,
                    core = save.conflictCore ?: save.core,
                    coreBuild = save.conflictCoreBuild,
                    romSha = save.conflictRomSha ?: save.romSha,
                ).clearConflict(),
        )
    }

    /**
     * Le serveur a été restauré (save_epoch différent) : les révisions repartent d’ailleurs.
     * Une copie modifiée localement devient un conflit avec la version restaurée ; une copie
     * inchangée prend la version restaurée, ou repart de zéro pour être renvoyée.
     */
    suspend fun onServerRestored(manifest: LibraryManifest) {
        manifest.games.forEach { store.prepareGame(it.playGameId, it.console) }
        checkpoint { store.checkpointAll() }
        val remotes = manifest.games.flatMap { it.saves.map { save -> save.copy(gameId = save.gameId.ifEmpty { it.playGameId }) } }
        val consoles = manifest.games.associate { it.playGameId to it.console }
        for (local in store.all()) {
            if (store.isGameActive(local.gameId)) {
                // No files are replaced while the core runs. An old epoch must never reuse a
                // coincidentally matching revision number from the restored server.
                store.put(local.copy(baseRevision = 0, baseSha = null).clearConflict())
                continue
            }
            val remote = remotes.firstOrNull { it.gameId == local.gameId && it.kind == local.kind && it.slot == local.slot }
            when {
                remote == null -> store.put(local.copy(baseRevision = 0, baseSha = null).clearConflict())

                remote.sha256.equals(local.localSha, ignoreCase = true) ->
                    store.put(local.copy(baseRevision = remote.revision, baseSha = remote.sha256).clearConflict())

                local.changedLocally -> keepConflict(local.clearConflict(), remote)

                else -> adopt(consoles[local.gameId] ?: local.console, remote, local)
            }
        }
    }

    /** Recover flushed native files before deciding whether a remote copy can replace them. */
    suspend fun checkpointNativeFiles() = checkpoint { store.checkpointAll() }

    private suspend fun checkpoint(block: suspend () -> Unit) {
        try {
            block()
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            throw ApiException.Http(409, "Les données locales sont conservées. Vérifiez l’espace libre puis réessayez la synchronisation.")
        }
    }

    private fun LocalSave.clearConflict() = copy(
        conflictRevision = null,
        conflictSha = null,
        conflictPlayedAt = null,
        conflictDevice = null,
        conflictCore = null,
        conflictCoreBuild = null,
        conflictRomSha = null,
    )

    companion object {
        /** Une sauvegarde envoyée depuis le navigateur n’a pas d’appareil. */
        const val WEB_DEVICE = "le web"
    }
}
