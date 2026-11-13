package ovh.verbois.retrohub.game

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use
import ovh.verbois.retrohub.api.ApiJson
import ovh.verbois.retrohub.core.sha256
import ovh.verbois.retrohub.sync.extractValidated
import ovh.verbois.retrohub.sync.zipFiles

/** Per-game cards/title data. The core is stopped during restore and paused during export. */
object NativeGameSaves {
    private const val ZERO = "00000000000000000000000000000000"
    private const val LIMIT = 256L * 1024 * 1024

    private fun manifest(console: String, gameId: String, sha: String, titleId: String?): JsonObject = buildJsonObject {
        put("format", if (console == "ps2") "ps2_card_zip_v1" else "azahar_game_zip_v1")
        put("game_id", gameId)
        put("rom_sha256", sha)
        if (console == "3ds") put("title_id", requireNotNull(titleId).lowercase())
    }

    private fun titleRoots(titleId: String): List<String> {
        require(Regex("00040000[0-9a-fA-F]{8}").matches(titleId))
        val low = titleId.takeLast(8).lowercase()
        return listOf(
            "Azahar/sdmc/Nintendo 3DS/$ZERO/$ZERO/title/00040000/$low/data",
            "Azahar/nand/$ZERO/title/00040000/$low/data",
        )
    }

    private fun normalized(name: String): String = name
        .replace(Regex("(?<=Azahar/sdmc/Nintendo 3DS/)[0-9a-fA-F]{32}/[0-9a-fA-F]{32}/"), "$ZERO/$ZERO/")
        .replace(Regex("(?<=Azahar/nand/)[0-9a-fA-F]{32}/"), "$ZERO/")

    fun export(
        fs: FileSystem,
        root: Path,
        console: String,
        gameId: String,
        sha: String,
        titleId: String?,
        destination: Path,
        markCheckpoint: Boolean = true,
    ): Boolean {
        val files = linkedMapOf<String, Path>()
        if (console == "ps2") {
            for (slot in 1..2) {
                val card = root / "pcsx2/memcards/Mcd00$slot.ps2"
                if (fs.exists(card) && validCard(fs, card)) files["cards/slot$slot.ps2"] = card
            }
        } else {
            if (titleId == null || !Regex("00040000[0-9a-fA-F]{8}").matches(titleId) || !fs.exists(root / "Azahar")) return false
            val roots = titleRoots(titleId)
            fs.listRecursively(root / "Azahar").filter { fs.metadata(it).isRegularFile }.forEach { file ->
                val name = normalized(file.relativeTo(root).toString())
                if (roots.any { name.startsWith("$it/") }) {
                    require(files.put(name, file) == null) // Ambiguous SD identities must never overwrite one another.
                }
            }
        }
        if (files.isEmpty()) return false
        require(files.values.sumOf { fs.metadata(it).size ?: 0L } <= LIMIT)
        fs.createDirectories(destination.parent!!)
        val descriptor = destination.parent!! / (destination.name + ".manifest.tmp")
        val temporary = destination.parent!! / (destination.name + ".zip.tmp")
        try {
            fs.write(descriptor) { writeUtf8(manifest(console, gameId, sha, titleId).toString()) }
            zipFiles(fs, files + ("manifest.json" to descriptor), temporary)
            fs.atomicMove(temporary, destination)
            if (markCheckpoint) checkpoint(fs, root, console, gameId, sha, titleId, fs.sha256(destination))
        } finally {
            fs.delete(descriptor, mustExist = false)
            fs.delete(temporary, mustExist = false)
        }
        return true
    }

    fun checkpoint(fs: FileSystem, root: Path, console: String, gameId: String, sha: String, titleId: String?, digest: String) {
        fs.write(root / ".restored-archive") { writeUtf8(digest + "\n" + manifest(console, gameId, sha, titleId)) }
    }

    fun install(fs: FileSystem, zip: Path, root: Path, console: String, gameId: String, sha: String, titleId: String?) {
        val roots = if (console == "3ds") titleRoots(requireNotNull(titleId)) else listOf("cards")
        recover(fs, root)
        val digest = fs.sha256(zip)
        val marker = root / ".restored-archive"
        val checkpoint = digest + "\n" + manifest(console, gameId, sha, titleId)
        if (fs.exists(marker) && fs.read(marker) { readUtf8() } == checkpoint) return
        val workspace = root.parent!! / (root.name + ".save-import")
        if (fs.exists(workspace)) fs.deleteRecursively(workspace)
        val staging = workspace / "archive"
        val candidate = workspace / "runtime"
        try {
            extractValidated(fs, zip, staging, LIMIT) { name ->
                name == "manifest.json" || if (console == "ps2") name in setOf("cards/slot1.ps2", "cards/slot2.ps2") else roots.any { name.startsWith("$it/") }
            }
            val descriptor = fs.read(staging / "manifest.json") { readUtf8() }
            require(ApiJson.decodeFromString(JsonObject.serializer(), descriptor) == manifest(console, gameId, sha, titleId))
            val payloads = fs.listRecursively(staging).filter { fs.metadata(it).isRegularFile && it != staging / "manifest.json" }.toList()
            require(payloads.isNotEmpty())
            fs.createDirectories(candidate)
            if (fs.exists(root)) {
                fs.listRecursively(root).forEach { source ->
                    val target = candidate / source.relativeTo(root)
                    val metadata = fs.metadata(source)
                    require(metadata.symlinkTarget == null)
                    if (metadata.isDirectory) {
                        fs.createDirectories(target)
                    } else {
                        fs.createDirectories(target.parent!!)
                        fs.copy(source, target)
                    }
                }
            }
            if (console == "ps2") {
                payloads.forEach { require(validCard(fs, it)) }
                val cards = staging / "cards"
                payloads.forEach { card -> fs.atomicMove(card, cards / "Mcd00${if (card.name == "slot1.ps2") 1 else 2}.ps2") }
                replaceDirectory(fs, cards, candidate / "pcsx2/memcards")
            } else {
                for (path in roots) {
                    val from = staging / path
                    // An absent root is an empty save, never data from a previous restore.
                    fs.createDirectories(from)
                    replaceDirectory(fs, from, candidate / path)
                }
            }
            fs.write(candidate / ".restored-archive") { writeUtf8(checkpoint) }
            replaceDirectory(fs, candidate, root)
        } finally {
            if (fs.exists(workspace)) fs.deleteRecursively(workspace)
        }
    }

    fun recover(fs: FileSystem, root: Path) {
        val backup = root.parent!! / (root.name + ".previous")
        if (fs.exists(backup) && !fs.exists(root)) fs.atomicMove(backup, root)
    }

    private fun validCard(fs: FileSystem, file: Path): Boolean {
        val size = fs.metadata(file).size ?: return false
        if ((listOf(8L, 16L, 32L, 64L).flatMap { listOf(it * 1024 * 1024, it * 1024 * 1024 / 512 * 528) }).none { it == size }) return false
        return fs.source(file).buffer().use { it.readUtf8(27) == "Sony PS2 Memory Card Format" }
    }

    private fun replaceDirectory(fs: FileSystem, from: Path, to: Path) {
        fs.createDirectories(to.parent!!)
        val backup = to.parent!! / (to.name + ".previous")
        if (fs.exists(backup) && !fs.exists(to)) fs.atomicMove(backup, to)
        if (fs.exists(backup)) fs.deleteRecursively(backup)
        if (fs.exists(to)) fs.atomicMove(to, backup)
        try {
            fs.atomicMove(from, to)
        } catch (error: Exception) {
            if (fs.exists(backup) && !fs.exists(to)) fs.atomicMove(backup, to)
            throw error
        }
        if (fs.exists(backup)) fs.deleteRecursively(backup)
    }
}
