package ovh.verbois.retrohub.sync

import okio.FileSystem
import okio.Path

/** Extrait l’archive [zip] dans [destination] ; refuse les chemins qui sortiraient du dossier. */
expect fun unzip(fs: FileSystem, zip: Path, destination: Path)

/** Écrit un ZIP « à plat » contenant [files] (sauvegardes GCI de la GameCube). */
expect fun zipFlat(fs: FileSystem, files: List<Path>, destination: Path)

/** Noms des fichiers d’une archive, sans l’extraire. */
expect fun zipEntries(fs: FileSystem, zip: Path): List<String>

internal fun safeEntryName(name: String): String? {
    val normalized = name.replace('\\', '/').trimStart('/')
    if (normalized.isEmpty() || normalized.endsWith("/")) return null
    if (normalized.split('/').any { it == ".." }) return null
    return normalized
}
