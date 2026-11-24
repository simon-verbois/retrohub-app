package ovh.verbois.retrohub.mods

import okio.FileSystem
import okio.Path.Companion.toPath
import ovh.verbois.retrohub.api.*
import ovh.verbois.retrohub.core.sha256
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.sync.zipFiles

internal val modEnvironment = ModEnvironment("3ds", "azahar", "build", "0004000000033600", "a".repeat(64))
internal val modGame = Game(
    LibraryGame(
        "zelda",
        "base",
        title = "Zelda",
        console = "3ds",
        rom = RomInfo(
            modEnvironment.romSha,
            1,
            "3ds",
            "zelda.3ds",
            "/rom",
            modEnvironment.titleId,
        ),
    ),
    null,
)

internal fun modRelease(
    id: String = "textures",
    installer: String = ModPlanner.TEXTURES,
    files: List<ModFile> = listOf(ModFile("image.png", 3, "b".repeat(64))),
): ModRelease {
    val hash = "c".repeat(64)
    return ModRelease(
        ModSelection(id, "1", "1080p"),
        "Mod $id",
        titleIds = listOf(modEnvironment.titleId!!),
        romSha256 = if (installer == ModPlanner.TEXTURES) emptyList() else listOf(modEnvironment.romSha),
        components = listOf(ModComponent(installer, ModArtifact(hash, 123, "/api/v1/native/mods/objects/$hash", files))),
    )
}

internal fun zipRelease(
    fs: FileSystem,
    id: String = "textures",
    installer: String = ModPlanner.TEXTURES,
    file: String = "image.png",
): Pair<ModRelease, ByteArray> {
    val source = "/fixture/$id/source".toPath()
    fs.createDirectories(source.parent!!)
    val data = byteArrayOf(1, 2, 3)
    fs.write(source) { write(data) }
    val zip = "/fixture/$id/pack.zip".toPath()
    zipFiles(fs, mapOf(file to source), zip)
    val bytes = fs.read(zip) { readByteArray() }
    val hash = sha256(bytes)
    val release = modRelease(id, installer, listOf(ModFile(file, 3, sha256(data))))
    return release.copy(
        components = listOf(
            ModComponent(installer, ModArtifact(hash, bytes.size.toLong(), "/api/v1/native/mods/objects/$hash", release.components.single().artifact!!.files)),
        ),
    ) to
        bytes
}

/** A release whose single component ships several small files, as one verified ZIP. */
internal fun zipReleaseFiles(fs: FileSystem, id: String, installer: String, files: List<String>): Pair<ModRelease, ByteArray> {
    val sources = files.mapIndexed { index, name ->
        val source = "/fixture/$id/source-$index".toPath()
        fs.createDirectories(source.parent!!)
        fs.write(source) { write(byteArrayOf(index.toByte(), 2, 3)) }
        name to source
    }.toMap()
    val zip = "/fixture/$id/pack.zip".toPath()
    zipFiles(fs, sources, zip)
    val bytes = fs.read(zip) { readByteArray() }
    val hash = sha256(bytes)
    val described = sources.map { (name, source) -> ModFile(name, 3, sha256(fs.read(source) { readByteArray() })) }
    val release = modRelease(id, installer, described)
    return release.copy(
        components = listOf(ModComponent(installer, ModArtifact(hash, bytes.size.toLong(), "/api/v1/native/mods/objects/$hash", described))),
    ) to bytes
}
