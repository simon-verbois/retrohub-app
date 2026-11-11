package ovh.verbois.retrohub.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Pending server contract, advertised explicitly before any of these routes are used. */
const val FEATURE_ACCOUNT_AVATARS = "account_avatars"

@Serializable
data class AccountAvatar(val id: String, val name: String, val url: String)

@Serializable
data class AvatarCatalog(val avatars: List<AccountAvatar> = emptyList(), @SerialName("default_avatar") val defaultAvatar: AccountAvatar? = null)

@Serializable
data class AvatarSelection(val avatar: AccountAvatar? = null)

/** Catalogue images must stay on the configured RetroHub server. */
fun avatarImageUrl(server: String, avatar: AccountAvatar?): String? {
    val path = avatar?.url ?: return null
    if (!path.startsWith("/api/v1/account/avatars/") || '\\' in path) return null
    return ServerAddress.resolve(server, path)
}
