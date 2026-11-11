package ovh.verbois.retrohub.game

import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use
import ovh.verbois.retrohub.sync.unzip
import ovh.verbois.retrohub.sync.zipEntries
import ovh.verbois.retrohub.sync.zipFlat

/**
 * Sauvegardes GameCube (docs/native-api.md, « Format des sauvegardes », gci_zip) : Dolphin range
 * chaque sauvegarde dans un fichier .gci du dossier « Card A ». On n’échange avec le serveur que
 * les fichiers de ce jeu (même code jeu), dans un ZIP à plat, sans toucher aux autres jeux.
 */
object GameCubeSaves {
    private val REGIONS = listOf("EUR", "USA", "JAP")

    /** Code jeu sur 4 caractères (« GALE »), lu dans l’en-tête du disque ou du conteneur. */
    fun gameCode(fs: FileSystem, rom: Path): String? {
        val header = fs.source(rom).buffer().use { source -> ByteArray(0x8010).also { buffer -> readFully(source, buffer) } }
        val offset =
            when {
                header.startsWith("RVZ\u0001") || header.startsWith("WIA\u0001") -> 0x58
                header.startsWith("CISO") -> 0x8000
                else -> 0
            }
        val code = header.copyOfRange(offset, offset + 4).decodeToString()
        return code.takeIf { it.length == 4 && it.all { c -> c.isLetterOrDigit() } }
    }

    /** Région du dossier de cartes mémoire de Dolphin, d’après la 4ᵉ lettre du code jeu. */
    fun region(code: String): String = when (code.getOrNull(3)) {
        'E' -> "USA"
        'J' -> "JAP"
        else -> "EUR"
    }

    fun cardDir(userDir: Path, region: String): Path = userDir / "GC" / region / "Card A"

    /** Remet les fichiers .gci de ce jeu (ZIP du serveur ou de l’appareil) dans le dossier de cartes. */
    fun install(fs: FileSystem, zip: Path, userDir: Path, code: String) {
        if (!fs.exists(zip)) return
        val card = cardDir(userDir, region(code))
        fs.createDirectories(card)
        mine(fs, userDir, code).forEach { fs.delete(it) }
        val staging = card / ".import"
        if (fs.exists(staging)) fs.deleteRecursively(staging)
        unzip(fs, zip, staging)
        fs.list(staging).filter { it.name.endsWith(".gci", ignoreCase = true) }.forEach { fs.atomicMove(it, card / it.name) }
        fs.deleteRecursively(staging)
    }

    /** ZIP des .gci de ce jeu, ou false s’il n’y en a pas encore. */
    fun export(fs: FileSystem, userDir: Path, code: String, destination: Path): Boolean {
        val files = mine(fs, userDir, code)
        if (files.isEmpty()) return false
        val temporary = destination.parent!! / (destination.name + ".zip.tmp")
        zipFlat(fs, files, temporary)
        fs.atomicMove(temporary, destination)
        return true
    }

    fun entries(fs: FileSystem, zip: Path): List<String> = zipEntries(fs, zip)

    /** Fichiers .gci dont l’en-tête porte le code de ce jeu (4 premiers octets). */
    private fun mine(fs: FileSystem, userDir: Path, code: String): List<Path> = REGIONS.map { cardDir(userDir, it) }.filter { fs.exists(it) }.flatMap { dir ->
        fs.list(dir).filter { it.name.endsWith(".gci", ignoreCase = true) }.filter { file ->
            fs.source(file).buffer().use { it.readUtf8(4) } == code
        }
    }

    private fun readFully(source: okio.BufferedSource, buffer: ByteArray) {
        var read = 0
        while (read < buffer.size) {
            val count = source.read(buffer, read, buffer.size - read)
            if (count == -1) break
            read += count
        }
    }

    private fun ByteArray.startsWith(prefix: String): Boolean = size >= prefix.length && prefix.indices.all { this[it] == prefix[it].code.toByte() }
}
