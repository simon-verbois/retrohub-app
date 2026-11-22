package ovh.verbois.retrohub.mods

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.openZip
import okio.use
import ovh.verbois.retrohub.api.ModArtifact
import ovh.verbois.retrohub.core.sha256
import ovh.verbois.retrohub.sync.FileDownloader

/** Immutable objects and prepared load trees; only a complete tree is ever published. */
class ModFiles(private val fs: FileSystem, private val root: Path, private val downloader: FileDownloader, private val freeBytes: (Path) -> Long?) {
    private fun objectPath(hash: String) = root / "objects" / hash.lowercase()
    private fun filesPath(hash: String) = root / "files" / hash.lowercase()

    fun installed(artifact: ModArtifact): Boolean {
        val marker = filesPath(artifact.sha256) / ".complete"
        return fs.exists(marker) && fs.read(marker) { readUtf8() }.equals(artifact.sha256, true)
    }

    private fun cachedObject(artifact: ModArtifact): Boolean {
        val path = objectPath(artifact.sha256)
        return fs.exists(path) && fs.metadata(path).size == artifact.size && fs.sha256(path).equals(artifact.sha256, true)
    }

    fun availableOffline(artifact: ModArtifact): Boolean = installed(artifact) || cachedObject(artifact)

    suspend fun install(plan: ModPlan, online: Boolean, progress: (Long, Long) -> Unit) {
        val artifacts = plan.releases.flatMap { it.components }.mapNotNull { it.artifact }.distinctBy { it.sha256.lowercase() }
        val needed = artifacts.filterNot(::installed)
        val required = needed.sumOf { it.size + it.files.sumOf { f -> f.size } } + artifacts.sumOf { it.files.sumOf { f -> f.size } }
        if (required > 0 && freeBytes(root)?.let { it < required + 64L * 1024 * 1024 } == true) {
            throw ModProblem("Libérez de l’espace sur cet appareil, puis réessayez l’installation.")
        }
        val total = needed.sumOf { it.size }
        var received = 0L
        for (artifact in artifacts) {
            currentCoroutineContext().ensureActive()
            if (installed(artifact)) continue
            val cached = cachedObject(artifact)
            if (!online && !cached) throw ModProblem("Connectez-vous pour terminer l’installation des mods.")
            val zip = if (cached) {
                objectPath(artifact.sha256)
            } else {
                downloader.downloadVerified(artifact.url, artifact.sha256, artifact.size, objectPath(artifact.sha256)) { count, _ ->
                    progress(received + count, total)
                }
            }
            val destination = filesPath(artifact.sha256)
            val staging = destination.parent!! / (destination.name + ".staging")
            if (fs.exists(staging)) fs.deleteRecursively(staging)
            try {
                extract(artifact, zip, staging)
                fs.write(staging / ".complete") { writeUtf8(artifact.sha256) }
                if (fs.exists(destination)) fs.deleteRecursively(destination)
                fs.atomicMove(staging, destination)
                received += artifact.size
                progress(received, total)
            } finally {
                if (fs.exists(staging)) fs.deleteRecursively(staging)
            }
        }
    }

    private suspend fun extract(artifact: ModArtifact, zip: Path, staging: Path) {
        validateModArchive(fs, zip, artifact)
        val expected = artifact.files.associateBy { it.path }
        fs.openZip(zip).use { archive ->
            var entries = 0
            val actual = mutableSetOf<String>()
            for (path in archive.listRecursively("/".toPath())) {
                currentCoroutineContext().ensureActive()
                if (++entries > ModPlanner.MAX_FILES * 2) throw ModProblem("L’archive du mod contient trop de fichiers.")
                val metadata = archive.metadata(path)
                require(metadata.symlinkTarget == null)
                if (metadata.isDirectory) continue
                val name = path.toString().removePrefix("/")
                val entry = expected[name] ?: throw ModProblem("L’archive du mod ne correspond pas à sa description.")
                require(metadata.isRegularFile && metadata.size == entry.size && actual.add(name))
                val target = staging / name
                fs.createDirectories(target.parent!!)
                archive.source(path).buffer().use { source ->
                    fs.sink(target).buffer().use { sink ->
                        var count = 0L
                        val bytes = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = source.read(bytes)
                            if (n == -1) break
                            count += n
                            require(count <= entry.size)
                            sink.write(bytes, 0, n)
                        }
                        require(count == entry.size)
                    }
                }
                require(fs.sha256(target).equals(entry.sha256, true))
            }
            require(actual == expected.keys)
        }
    }

    /** Return the exact load tree for a launch; installed data is rechecked before use. */
    suspend fun prepare(plan: ModPlan, gameRoot: Path) {
        val descriptors = plan.releases.flatMap { it.components }.mapNotNull { it.artifact }.associate {
            it.sha256.lowercase() to it.files.associateBy { f -> f.path }
        }
        val cheatFile = plan.titleId?.let { gameRoot / "Azahar" / "cheats" / "${it.uppercase()}.txt" }
        val cheatText = plan.cheats.joinToString("\n") { cheat ->
            "[${cheat.name.ifBlank { "Mod" }}]\n" + (if (cheat.enabled) "*citra_enabled\n" else "") + cheat.code + "\n"
        }
        if (cheatFile != null && (plan.cheats.isNotEmpty() || fs.exists(cheatFile))) {
            fs.createDirectories(cheatFile.parent!!)
            val temporary = cheatFile.parent!! / (cheatFile.name + ".tmp")
            fs.write(temporary) { writeUtf8(cheatText) }
            fs.atomicMove(temporary, cheatFile)
        }
        val load = gameRoot / "Azahar" / "load"
        val signature = load / ".profile"
        if (fs.exists(signature) && fs.read(signature) { readUtf8() } == plan.fingerprint.orEmpty() && verifyTree(plan, load, descriptors)) return
        val staging = gameRoot / "Azahar" / "load.staging"
        if (fs.exists(staging)) fs.deleteRecursively(staging)
        fs.createDirectories(staging)
        try {
            for (entry in plan.files) {
                currentCoroutineContext().ensureActive()
                val source = filesPath(entry.artifact) / entry.source
                val descriptor = descriptors.getValue(entry.artifact).getValue(entry.source)
                if (!fs.exists(source) || fs.metadata(source).size != descriptor.size || !fs.sha256(source).equals(descriptor.sha256, true)) {
                    fs.delete(filesPath(entry.artifact) / ".complete", mustExist = false)
                    throw ModProblem("Un mod doit être réparé. Ouvrez Mods et réinstallez le profil.")
                }
                val target = staging / entry.destination
                fs.createDirectories(target.parent!!)
                fs.copy(source, target)
            }
            fs.write(staging / ".profile") { writeUtf8(plan.fingerprint.orEmpty()) }
            // Each full profile has its own runtime, so replacing this stopped load tree cannot
            // change another profile or its saved data. Interrupted preparation is retried.
            if (fs.exists(load)) fs.deleteRecursively(load)
            fs.atomicMove(staging, load)
        } finally {
            if (fs.exists(staging)) fs.deleteRecursively(staging)
        }
    }

    private fun verifyTree(plan: ModPlan, load: Path, descriptors: Map<String, Map<String, ovh.verbois.retrohub.api.ModFile>>): Boolean {
        val expected = plan.files.map { it.destination }.toSet() + ".profile"
        val actual = fs.listRecursively(load).filter { !fs.metadata(it).isDirectory }.take(expected.size + 1).toList()
        if (actual.any { fs.metadata(it).symlinkTarget != null } || actual.map { it.relativeTo(load).toString() }.toSet() != expected) return false
        return plan.files.all { entry ->
            val descriptor = descriptors.getValue(entry.artifact).getValue(entry.source)
            val path = load / entry.destination
            fs.exists(path) && fs.metadata(path).size == descriptor.size && fs.sha256(path).equals(descriptor.sha256, true)
        }
    }

    fun remove(artifacts: Set<String>, retained: Set<String>) {
        for (hash in artifacts - retained) {
            val extracted = filesPath(hash)
            if (fs.exists(extracted)) fs.deleteRecursively(extracted)
            fs.delete(objectPath(hash), mustExist = false)
            fs.delete(root / "objects" / "$hash.part", mustExist = false)
        }
    }
}
