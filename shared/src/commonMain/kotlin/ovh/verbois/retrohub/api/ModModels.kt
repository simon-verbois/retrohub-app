package ovh.verbois.retrohub.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ovh.verbois.retrohub.game.Cheat

const val FEATURE_GAME_MODS = "game_mods_v1"

/** Additive native contract specified in docs/server-requests.md. Unknown installers stay disabled. */
@Serializable
data class ModSelection(val id: String, val version: String, val variant: String = "default")

@Serializable
data class ModFile(val path: String, val size: Long, val sha256: String)

@Serializable
data class ModArtifact(val sha256: String, val size: Long, val url: String, val files: List<ModFile>)

@Serializable
data class ModComponent(val installer: String, val artifact: ModArtifact? = null, val cheats: List<Cheat> = emptyList())

@Serializable
data class ModRelease(
    val selection: ModSelection,
    val name: String,
    val author: String = "",
    @SerialName("source_url") val sourceUrl: String? = null,
    val license: String = "",
    @SerialName("variant_label") val variantLabel: String = "",
    val console: String = "3ds",
    val core: String = "azahar",
    @SerialName("title_ids") val titleIds: List<String>,
    @SerialName("rom_sha256") val romSha256: List<String> = emptyList(),
    @SerialName("core_builds") val coreBuilds: List<String> = emptyList(),
    val requires: List<ModSelection> = emptyList(),
    val conflicts: List<String> = emptyList(),
    val components: List<ModComponent>,
)

@Serializable
data class ModProfile(
    val revision: Int = 0,
    val selections: List<ModSelection> = emptyList(),
    @SerialName("gameplay_fingerprint") val gameplayFingerprint: String? = null,
    @SerialName("save_context_id") val saveContextId: String? = null,
    val saves: List<RemoteSave> = emptyList(),
)

@Serializable
data class ModProfileUpdate(
    @SerialName("base_revision") val baseRevision: Int,
    val selections: List<ModSelection>,
    @SerialName("gameplay_fingerprint") val gameplayFingerprint: String?,
)

@Serializable
data class ModCatalogue(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    @SerialName("game_id") val gameId: String,
    val releases: List<ModRelease> = emptyList(),
    val profile: ModProfile = ModProfile(),
    @SerialName("profile_history") val profileHistory: List<ModProfile> = emptyList(),
)

/** Every retained gameplay context remains in the library, including inactive profiles. */
@Serializable
data class ModSaveContext(
    @SerialName("game_id") val gameId: String,
    @SerialName("gameplay_fingerprint") val gameplayFingerprint: String,
    val saves: List<RemoteSave> = emptyList(),
)

fun LibraryGame.withModProgressions(): List<LibraryGame> = listOf(this) + modSaveContexts.map { context ->
    require(context.gameId != playGameId && context.saves.all { it.gameId == context.gameId })
    copy(playGameId = context.gameId, saves = context.saves, modSaveContexts = emptyList())
}
