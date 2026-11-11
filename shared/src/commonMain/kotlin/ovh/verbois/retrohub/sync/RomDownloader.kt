package ovh.verbois.retrohub.sync

import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use
import ovh.verbois.retrohub.api.ApiException
import ovh.verbois.retrohub.api.BiosInfo
import ovh.verbois.retrohub.api.RetroHubApi
import ovh.verbois.retrohub.api.RomInfo
import ovh.verbois.retrohub.api.copy
import ovh.verbois.retrohub.core.sha256

/** La ROM a changé sur le serveur (409) : relire le catalogue avant de recommencer. */
class RomChangedException : Exception("Une nouvelle version du jeu est disponible sur le serveur.")

/** Le fichier reçu ne correspond pas à l’empreinte annoncée, même après une nouvelle tentative. */
class CorruptedDownloadException : Exception("Le téléchargement est arrivé abîmé. Réessayez.")

/**
 * Téléchargement reprenable des ROM et BIOS : un fichier « .part » garde les octets reçus,
 * la suite est demandée avec Range ; un 200 complet (stockage S3) recommence au début.
 * Le fichier final n’apparaît qu’une fois son SHA-256 vérifié.
 */
class FileDownloader(private val api: RetroHubApi, private val fs: FileSystem) {
    suspend fun downloadRom(rom: RomInfo, target: Path, onProgress: (received: Long, total: Long) -> Unit = { _, _ -> }): Path =
        download(rom.url, rom.sha256, rom.size, target, onProgress)

    suspend fun downloadBios(bios: BiosInfo, target: Path): Path = download(bios.url, bios.sha256, null, target) { _, _ -> }

    private suspend fun download(url: String, sha256: String, size: Long?, target: Path, onProgress: (Long, Long) -> Unit): Path {
        target.parent?.let { fs.createDirectories(it) }
        if (fs.exists(target) && fs.sha256(target).equals(sha256, ignoreCase = true)) return target
        val partial = target.parent!! / (target.name + ".part")
        repeat(2) {
            var have = if (fs.exists(partial)) fs.metadata(partial).size ?: 0L else 0L
            if (size != null && have > size) {
                fs.delete(partial)
                have = 0
            }
            if (size == null || have < size) fetch(url, partial, have, size ?: 0, onProgress)
            if (fs.sha256(partial).equals(sha256, ignoreCase = true)) {
                fs.atomicMove(partial, target)
                return target
            }
            // Fichier partiel incohérent : on repart de zéro une fois.
            fs.delete(partial)
        }
        throw CorruptedDownloadException()
    }

    private suspend fun fetch(url: String, partial: Path, have: Long, size: Long, onProgress: (Long, Long) -> Unit) {
        try {
            api.download(url, rangeStart = have) { status, contentRange, body ->
                // Seul un 206 qui reprend exactement à nos octets s’ajoute ; sinon on réécrit tout.
                val append = status == 206 && have > 0 && contentRange?.startsWith("bytes $have-") == true
                val total = contentRange?.substringAfterLast('/')?.toLongOrNull() ?: size
                var received = if (append) have else 0L
                (if (append) fs.appendingSink(partial) else fs.sink(partial)).buffer().use { sink ->
                    copy(body) { buffer, count ->
                        sink.write(buffer, 0, count)
                        received += count
                        onProgress(received, total)
                    }
                }
            }
        } catch (error: ApiException.Http) {
            when (error.status) {
                409 -> throw RomChangedException()

                // Plage refusée : le fichier partiel ne correspond plus, on recommence.
                416 -> fs.delete(partial)

                else -> throw error
            }
        }
    }
}
