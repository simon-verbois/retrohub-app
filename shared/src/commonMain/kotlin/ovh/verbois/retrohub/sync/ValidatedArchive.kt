package ovh.verbois.retrohub.sync

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.openZip
import okio.use

/** Bounded, allowlisted extraction. Callers publish the staging directory only after validation. */
fun extractValidated(fs: FileSystem, zip: Path, destination: Path, limit: Long, allowed: (String) -> Boolean) {
    val names = zipEntries(fs, zip)
    require(names.size <= 10000)
    require(names.map { it.lowercase() }.distinct().size == names.size)
    names.forEach { name ->
        require(!name.startsWith('/') && !name.contains('\\') && !name.contains(':'))
        require(name.split('/').all { it.isNotEmpty() && it != "." && it != ".." })
        require(allowed(name))
    }
    fs.createDirectories(destination)
    fs.openZip(zip).use { archive ->
        var remaining = limit
        for (name in names) {
            val entry = "/$name".toPath()
            val metadata = archive.metadata(entry)
            require(metadata.isRegularFile && metadata.symlinkTarget == null)
            require((metadata.size ?: limit + 1) <= remaining)
            val target = destination / name
            fs.createDirectories(target.parent!!)
            archive.source(entry).buffer().use { source ->
                fs.sink(target).buffer().use { sink ->
                    val bytes = ByteArray(8192)
                    while (true) {
                        val count = source.read(bytes)
                        if (count == -1) break
                        remaining -= count
                        require(remaining >= 0)
                        sink.write(bytes, 0, count)
                    }
                }
            }
        }
    }
}
