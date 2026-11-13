package ovh.verbois.retrohub.game

import okio.FileSystem
import okio.Path
import ovh.verbois.retrohub.sync.extractValidated

/** Libretro cores expect a disc or playlist, not the server's ZIP container. */
object GameContent {
    fun prepare(fs: FileSystem, rom: Path, sha: String, console: String): Path {
        if (console !in setOf("psx", "dreamcast", "ps2") || !rom.name.endsWith(".zip", ignoreCase = true)) return rom
        val directory = rom.parent!! / "content"
        val marker = directory / ".sha256"
        if (!fs.exists(marker) || fs.read(marker) { readUtf8() } != sha) {
            if (fs.exists(directory)) fs.deleteRecursively(directory)
            extractValidated(fs, rom, directory, 16L * 1024 * 1024 * 1024) { true }
            fs.write(marker) { writeUtf8(sha) }
        }
        val files = fs.listRecursively(directory).filter { fs.metadata(it).isRegularFile }.sorted().toList()
        val extensions = when (console) {
            "ps2" -> listOf("m3u", "chd", "iso", "cue", "bin")
            "psx" -> listOf("m3u", "cue", "chd", "pbp", "iso", "bin")
            "dreamcast" -> listOf("m3u", "gdi", "cue", "chd", "cdi")
            else -> emptyList()
        }
        for (extension in extensions) {
            val candidates = files.filter { it.name.substringAfterLast('.', "").equals(extension, true) }
            if (candidates.isEmpty()) continue
            if (extension == "bin" && candidates.size > 1) break
            if (console == "psx" && candidates.size > 1 && extension != "m3u" && extension != "bin") {
                val playlist = directory / "discs.m3u"
                fs.write(playlist) { candidates.forEach { writeUtf8(it.relativeTo(directory).toString() + "\n") } }
                return playlist
            }
            return candidates.first()
        }
        throw IllegalArgumentException("Aucun disque jouable dans cette archive. Choisissez une autre version du jeu dans le catalogue.")
    }
}
