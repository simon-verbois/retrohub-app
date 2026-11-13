package ovh.verbois.retrohub.game

import okio.FileSystem
import okio.Path
import ovh.verbois.retrohub.api.RetroHubApi
import ovh.verbois.retrohub.api.SystemResource
import ovh.verbois.retrohub.api.copy
import ovh.verbois.retrohub.core.sha256
import ovh.verbois.retrohub.sync.extractValidated

/** Authenticated, checksum-verified private resources installed before the core starts. */
class SystemResources(private val api: RetroHubApi, private val fs: FileSystem) {
    suspend fun install(console: String, resources: List<SystemResource>, root: Path, online: Boolean) {
        val required = if (console == "ps2") setOf("bios", "resources") else emptySet()
        require(resources.map { it.kind }.containsAll(required)) {
            "Importez le BIOS et les ressources PS2 dans les réglages de votre serveur, puis synchronisez la bibliothèque."
        }
        for (resource in resources) {
            require(resource.size in 1..MAX && resource.files.isNotEmpty())
            require(resource.files.sumOf { it.size } <= MAX && resource.files.all { it.size in 1..MAX })
            require(resource.files.all { allowed(console, resource.kind, it.path) })
            require(resource.files.map { it.path.lowercase() }.distinct().size == resource.files.size)
            val installed = resource.files.all { entry ->
                val file = root / entry.path
                fs.exists(file) && fs.metadata(file).size == entry.size && fs.sha256(file).equals(entry.sha256, true)
            }
            if (installed) continue
            require(online) { "Connectez-vous pour installer les fichiers de cette console." }
            val staging = root / ".resource-import"
            if (fs.exists(staging)) fs.deleteRecursively(staging)
            fs.createDirectories(staging)
            try {
                val objectFile = staging / "object"
                var count = 0L
                api.download(resource.url) { _, _, body ->
                    fs.write(objectFile) {
                        copy(body) { bytes, n ->
                            count += n
                            require(count <= MAX)
                            write(bytes, 0, n)
                        }
                    }
                }
                require(count == resource.size && fs.sha256(objectFile).equals(resource.sha256, true))
                val files = staging / "files"
                if (resource.format == "raw") {
                    require(resource.files.size == 1)
                    val file = files / resource.files.single().path
                    fs.createDirectories(file.parent!!)
                    fs.atomicMove(objectFile, file)
                } else {
                    require(resource.format == "zip")
                    val names = resource.files.map { it.path }.toSet()
                    extractValidated(fs, objectFile, files, MAX) { it in names }
                }
                for (entry in resource.files) {
                    val file = files / entry.path
                    require(fs.exists(file) && fs.metadata(file).size == entry.size && fs.sha256(file).equals(entry.sha256, true))
                }
                // The game is stopped. Each complete verified file replaces its old version atomically.
                for (entry in resource.files) {
                    val target = root / entry.path
                    fs.createDirectories(target.parent!!)
                    fs.atomicMove(files / entry.path, target)
                }
            } finally {
                if (fs.exists(staging)) fs.deleteRecursively(staging)
            }
        }
    }

    private fun allowed(console: String, kind: String, path: String): Boolean {
        if (path.startsWith('/') || path.contains('\\') || path.contains(':') || path.split('/').any { it == "." || it == ".." || it.isEmpty() }) return false
        return when {
            console == "ps2" && kind == "bios" -> path == "pcsx2/bios/bios.bin"

            console == "ps2" && kind == "resources" -> path.startsWith("pcsx2/resources/")

            console == "3ds" && kind == "system" ->
                path == "Azahar/sysdata/shared_font.bin" ||
                    Regex("Azahar/nand/[0-9a-f]{32}/title/0004009b/00010[012]02/content/[0-9a-f]+\\.(app|tmd)").matches(path)

            else -> false
        }
    }

    private companion object {
        const val MAX = 64L * 1024 * 1024
    }
}
