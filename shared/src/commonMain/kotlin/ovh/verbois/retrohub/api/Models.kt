package ovh.verbois.retrohub.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Modèles de l’API native, voir ../retrohub/docs/native-api.md (le document fait foi). */

const val SUPPORTED_API_VERSION = 1

@Serializable
data class ServerStatus(
    val name: String = "RetroHub",
    @SerialName("server_version") val serverVersion: String = "",
    @SerialName("api_version") val apiVersion: Int,
    val auth: AuthModes = AuthModes(),
    val limits: Limits = Limits(),
    val features: List<String> = emptyList(),
    val apps: Apps? = null,
)

@Serializable
data class AuthModes(val password: Boolean = true, val pairing: Boolean = true, val oidc: Boolean = false)

@Serializable
data class Limits(@SerialName("max_save_mb") val maxSaveMb: Int = 64, @SerialName("max_upload_mb") val maxUploadMb: Int = 1024)

@Serializable
data class Apps(val android: AppRelease? = null, val ios: AppRelease? = null)

@Serializable
data class AppRelease(
    @SerialName("min_version") val minVersion: String? = null,
    @SerialName("latest_version") val latestVersion: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
)

@Serializable
data class TokenRequest(val username: String, val password: String, @SerialName("device_name") val deviceName: String, val platform: String)

@Serializable
data class PairRequest(val code: String, @SerialName("device_name") val deviceName: String, val platform: String)

@Serializable
data class Device(val id: String, val name: String, val platform: String = "")

@Serializable
data class AccountUser(val id: String, val username: String, @SerialName("is_admin") val isAdmin: Boolean = false, val avatar: AccountAvatar? = null)

@Serializable
data class TokenResponse(val token: String, val device: Device, val user: AccountUser)

@Serializable
data class DeviceItem(
    val id: String,
    val name: String,
    val platform: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    val current: Boolean = false,
)

@Serializable
data class LibraryManifest(
    @SerialName("api_version") val apiVersion: Int = SUPPORTED_API_VERSION,
    @SerialName("server_version") val serverVersion: String = "",
    @SerialName("save_epoch") val saveEpoch: String = "initial",
    @SerialName("generated_at") val generatedAt: String? = null,
    val games: List<LibraryGame> = emptyList(),
)

@Serializable
data class LibraryGame(
    val id: String,
    @SerialName("play_game_id") val playGameId: String,
    @SerialName("progression_choice_required") val progressionChoiceRequired: Boolean = false,
    @SerialName("save_contexts") val saveContexts: List<SaveContext> = emptyList(),
    val title: String,
    val console: String,
    @SerialName("cover_url") val coverUrl: String? = null,
    val screenshots: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val genre: String? = null,
    val year: Int? = null,
    val favorite: Boolean = false,
    @SerialName("played_seconds") val playedSeconds: Long = 0,
    @SerialName("last_played_at") val lastPlayedAt: String? = null,
    val installed: Boolean = false,
    val rom: RomInfo? = null,
    val bios: BiosInfo? = null,
    @SerialName("system_resources") val systemResources: List<SystemResource> = emptyList(),
    val saves: List<RemoteSave> = emptyList(),
)

@Serializable
data class SaveContext(
    @SerialName("game_id") val gameId: String,
    val title: String = "",
    @SerialName("version_id") val versionId: String? = null,
    val label: String = "",
    val installed: Boolean = false,
    val saves: Int = 0,
    @SerialName("played_seconds") val playedSeconds: Long = 0,
    @SerialName("last_played_at") val lastPlayedAt: String? = null,
    @SerialName("last_save") val lastSave: LastSave? = null,
)

@Serializable
data class LastSave(val kind: String = "sram", val slot: Int = 0, @SerialName("played_at") val playedAt: String? = null, val device: Device? = null)

@Serializable
data class RomInfo(
    val sha256: String,
    val size: Long,
    val extension: String,
    val filename: String,
    val url: String,
    @SerialName("native_title_id") val nativeTitleId: String? = null,
)

@Serializable
data class BiosInfo(val url: String, val sha256: String)

@Serializable
data class RemoteSave(
    val id: String = "",
    @SerialName("game_id") val gameId: String = "",
    val kind: String,
    val slot: Int,
    val revision: Int,
    val sha256: String,
    val size: Long = 0,
    val core: String = "",
    @SerialName("core_build") val coreBuild: String? = null,
    @SerialName("rom_sha256") val romSha256: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("played_at") val playedAt: String? = null,
    val device: Device? = null,
)

@Serializable
data class ConsoleCapabilities(
    val id: String,
    val name: String,
    val extensions: List<String> = emptyList(),
    val cores: List<String> = emptyList(),
    @SerialName("native_cores") val nativeCores: List<String> = emptyList(),
    @SerialName("playable_in_browser") val playableInBrowser: Boolean = false,
    val bios: Boolean = false,
    @SerialName("max_save_mb") val maxSaveMb: Int? = null,
    @SerialName("save_formats") val saveFormats: Map<String, String> = emptyMap(),
) {
    val acceptedCores: List<String> get() = cores + nativeCores
}

@Serializable
data class OfflineSession(
    @SerialName("client_id") val clientId: String,
    @SerialName("game_id") val gameId: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String,
    @SerialName("played_seconds") val playedSeconds: Long,
)

@Serializable
data class SessionBatch(val sessions: List<OfflineSession>)

@Serializable
data class SessionResult(@SerialName("client_id") val clientId: String, val status: String, val reason: String? = null)

@Serializable
data class SessionResults(val results: List<SessionResult> = emptyList())

@Serializable
data class LibraryUpdate(val favorite: Boolean? = null, @SerialName("version_id") val versionId: String? = null)

@Serializable
data class BrowserSession(
    val id: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    val device: String = "",
    val current: Boolean = false,
)

@Serializable
data class AccountSecurity(
    @SerialName("local_password") val localPassword: Boolean = true,
    val oidc: Boolean = false,
    val sessions: List<BrowserSession> = emptyList(),
)

@Serializable
data class PairingCode(val code: String, @SerialName("expires_at") val expiresAt: String? = null)

@Serializable
data class PasswordChange(@SerialName("current_password") val currentPassword: String, @SerialName("new_password") val newPassword: String)

@Serializable
data class DeviceRename(val name: String)

@Serializable
data class SystemResource(val kind: String, val format: String, val sha256: String, val size: Long, val files: List<SystemResourceFile>, val url: String)

@Serializable
data class SystemResourceFile(val path: String, val size: Long, val sha256: String)
