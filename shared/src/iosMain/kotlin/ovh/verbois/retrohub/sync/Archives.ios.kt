package ovh.verbois.retrohub.sync

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.openZip

// iOS : lecture par le ZipFileSystem d’Okio. L’écriture des ZIP GCI (GameCube) arrivera avec le
// frontend iOS ; à vérifier sur un Mac (voir docs/ios.md).

actual fun unzip(fs: FileSystem, zip: Path, destination: Path) {
    val archive = fs.openZip(zip)
    fs.createDirectories(destination)
    for (entry in archive.listRecursively("/".toPath())) {
        val metadata = archive.metadata(entry)
        if (metadata.isDirectory) continue
        val name = safeEntryName(entry.toString()) ?: continue
        val target = destination / name
        target.parent?.let { fs.createDirectories(it) }
        archive.read(entry) { fs.write(target) { writeAll(this@read) } }
    }
}

actual fun zipFlat(fs: FileSystem, files: List<Path>, destination: Path): Unit =
    throw UnsupportedOperationException("Sauvegardes GameCube pas encore disponibles sur iOS")

actual fun zipEntries(fs: FileSystem, zip: Path): List<String> {
    val archive = fs.openZip(zip)
    return archive.listRecursively("/".toPath()).filter { !archive.metadata(it).isDirectory }.map { it.toString().trimStart('/') }.toList()
}

actual fun zipFiles(fs: FileSystem, files: Map<String, Path>, destination: Path): Unit =
    throw UnsupportedOperationException("Sauvegardes natives pas encore disponibles sur iOS")
