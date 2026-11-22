package ovh.verbois.retrohub.mods

import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use
import ovh.verbois.retrohub.api.ModArtifact

/** Inspect original central-directory names before Okio normalizes paths or merges duplicates.
 * ZIP64 is supported; metadata is bounded without inflating a potentially hostile archive.
 */
internal fun validateModArchive(fs: FileSystem, zip: Path, artifact: ModArtifact) {
    fs.openReadOnly(zip).use { handle ->
        val length = handle.size()
        require(length >= 22)
        fun read(offset: Long, size: Long): Buffer {
            require(offset >= 0 && size in 0..(128L * 1024) && offset <= length - size)
            return Buffer().write(handle.source(offset).buffer().use { it.readByteArray(size) })
        }
        val tailOffset = (length - 65_557).coerceAtLeast(0)
        val tail = read(tailOffset, length - tailOffset).readByteArray()
        fun u16(bytes: ByteArray, offset: Int) = (bytes[offset].toInt() and 255) or ((bytes[offset + 1].toInt() and 255) shl 8)
        var end = tail.size - 22
        while (end >= 0 && !(
                tail[end] == 0x50.toByte() && tail[end + 1] == 0x4b.toByte() && tail[end + 2] == 5.toByte() && tail[end + 3] == 6.toByte() &&
                    end + 22 + u16(tail, end + 20) == tail.size
                )
        ) {
            end--
        }
        require(end >= 0)
        val endOffset = tailOffset + end
        val record = read(endOffset + 4, 16)
        require(record.readShortLe().toInt() == 0 && record.readShortLe().toInt() == 0)
        val localCount = record.readShortLe().toInt() and 65535
        var count = (record.readShortLe().toInt() and 65535).toLong()
        var centralSize = record.readIntLe().toLong() and 0xffffffffL
        var centralOffset = record.readIntLe().toLong() and 0xffffffffL
        require(localCount.toLong() == count)
        if (count == 65535L || centralSize == 0xffffffffL || centralOffset == 0xffffffffL) {
            val locator = read(endOffset - 20, 20)
            require(locator.readIntLe() == 0x07064b50 && locator.readIntLe() == 0)
            val zip64Offset = locator.readLongLe()
            require(locator.readIntLe() == 1)
            val extended = read(zip64Offset, 56)
            require(extended.readIntLe() == 0x06064b50)
            require(extended.readLongLe() >= 44)
            extended.skip(4)
            require(extended.readIntLe() == 0 && extended.readIntLe() == 0)
            val diskCount = extended.readLongLe()
            count = extended.readLongLe()
            require(count == diskCount)
            centralSize = extended.readLongLe()
            centralOffset = extended.readLongLe()
        }
        require(count in 1..(ModPlanner.MAX_FILES * 2L) && centralSize in 1..(128L * 1024 * 1024))
        require(centralOffset >= 0 && centralOffset <= endOffset - centralSize)
        val expected = artifact.files.map { it.path }.toSet()
        val directories = mutableSetOf<String>()
        for (name in expected) {
            var parent = name.substringBeforeLast('/', "")
            while (parent.isNotEmpty()) {
                directories += parent
                parent = parent.substringBeforeLast('/', "")
            }
        }
        val seen = mutableSetOf<String>()
        val allNames = mutableSetOf<String>()
        var offset = centralOffset
        repeat(count.toInt()) {
            val header = read(offset, 46)
            require(header.readIntLe() == 0x02014b50)
            header.skip(4)
            val flags = header.readShortLe().toInt() and 65535
            require(flags and 1 == 0)
            header.skip(18)
            val nameSize = header.readShortLe().toInt() and 65535
            val extraSize = header.readShortLe().toInt() and 65535
            val commentSize = header.readShortLe().toInt() and 65535
            require(header.readShortLe().toInt() == 0)
            header.skip(2)
            val mode = header.readIntLe() ushr 16
            require(mode and 0xf000 != 0xa000)
            require(nameSize in 1..513)
            val name = read(offset + 46, nameSize.toLong()).readUtf8()
            val directory = name.endsWith('/')
            val clean = if (directory) name.dropLast(1) else name
            require(ModPlanner.safePath(clean) && allNames.add(clean.lowercase()))
            if (directory) require(clean in directories) else require(name in expected && seen.add(name))
            offset += 46L + nameSize + extraSize + commentSize
            require(offset <= centralOffset + centralSize)
        }
        require(offset == centralOffset + centralSize && seen == expected)
    }
}
