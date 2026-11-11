package ovh.verbois.retrohub.core

import okio.Buffer
import okio.FileSystem
import okio.HashingSink
import okio.Path
import okio.blackholeSink
import okio.buffer
import okio.use

fun sha256(bytes: ByteArray): String {
    val hashing = HashingSink.sha256(blackholeSink())
    val buffer = Buffer()
    var offset = 0
    while (offset < bytes.size) {
        val count = minOf(64 * 1024, bytes.size - offset)
        buffer.write(bytes, offset, count)
        hashing.write(buffer, count.toLong())
        offset += count
    }
    return hashing.hash.hex()
}

/** SHA-256 d’un fichier, lu par blocs : une ROM de plusieurs gigaoctets ne charge pas la mémoire. */
fun FileSystem.sha256(path: Path): String {
    val hashing = HashingSink.sha256(blackholeSink())
    val sink = hashing.buffer()
    source(path).use { source -> sink.writeAll(source) }
    sink.flush()
    return hashing.hash.hex()
}
