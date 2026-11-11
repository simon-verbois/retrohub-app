package ovh.verbois.retrohub.core

import okio.Buffer
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.HashingSink
import okio.Path
import okio.blackholeSink
import okio.buffer
import okio.use

fun sha256(bytes: ByteArray): String = bytes.toByteString().sha256().hex()

/** SHA-256 d’un fichier, lu par blocs : une ROM de plusieurs gigaoctets ne charge pas la mémoire. */
fun FileSystem.sha256(path: Path): String {
    val hashing = HashingSink.sha256(blackholeSink())
    val sink = hashing.buffer()
    source(path).use { source -> sink.writeAll(source) }
    sink.flush()
    return hashing.hash.hex()
}

fun Buffer.sha256Hex(): String = sha256().hex()
