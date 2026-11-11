package ovh.verbois.retrohub.sync

import okio.FileSystem
import okio.Path
import okio.buffer
import okio.sink
import okio.source
import okio.use
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

actual fun unzip(fs: FileSystem, zip: Path, destination: Path) {
    fs.createDirectories(destination)
    ZipInputStream(fs.source(zip).buffer().inputStream()).use { input ->
        while (true) {
            val entry = input.nextEntry ?: break
            val name = safeEntryName(entry.name)
            if (!entry.isDirectory && name != null) {
                val target = destination / name
                target.parent?.let { fs.createDirectories(it) }
                fs.sink(target).buffer().use { it.writeAll(input.source()) }
            }
            input.closeEntry()
        }
    }
}

actual fun zipFlat(fs: FileSystem, files: List<Path>, destination: Path) {
    destination.parent?.let { fs.createDirectories(it) }
    ZipOutputStream(fs.sink(destination).buffer().outputStream()).use { output ->
        for (file in files.sortedBy { it.name }) {
            output.putNextEntry(ZipEntry(file.name).apply { time = 0 })
            fs.source(file).buffer().use { source -> source.inputStream().copyTo(output) }
            output.closeEntry()
        }
    }
}

actual fun zipEntries(fs: FileSystem, zip: Path): List<String> = ZipInputStream(fs.source(zip).buffer().inputStream()).use { input ->
    generateSequence { input.nextEntry }.filterNot { it.isDirectory }.map { it.name }.toList()
}
