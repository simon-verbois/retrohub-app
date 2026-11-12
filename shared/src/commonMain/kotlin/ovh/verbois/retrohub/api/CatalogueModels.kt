package ovh.verbois.retrohub.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CataloguePage(val items: List<CatalogueGame> = emptyList(), val total: Int = 0, val page: Int = 1, val limit: Int = 24)

@Serializable
data class CatalogueGame(
    val id: String,
    val title: String,
    val console: String,
    @SerialName("cover_url") val coverUrl: String? = null,
    val description: String = "",
    val languages: List<String> = emptyList(),
    val genre: String? = null,
    val year: Int? = null,
    val installed: Boolean = false,
    @SerialName("version_id") val versionId: String? = null,
    val versions: List<CatalogueVersion> = emptyList(),
)

@Serializable
data class CatalogueVersion(
    val id: String,
    val label: String,
    val languages: List<String> = emptyList(),
    val source: String = "",
    val available: Boolean = false,
    val downloadable: Boolean = false,
)

@Serializable
data class CatalogueSource(val id: String, val name: String)

@Serializable
data class SourceFile(val id: String, val name: String, val size: String = "", val languages: List<String> = emptyList())

@Serializable
data class ServerJob(
    val id: String,
    val label: String = "",
    val status: String,
    val progress: Float = 0f,
    @SerialName("needs_file_choice") val needsFileChoice: Boolean = false,
) {
    val active: Boolean get() = status == "queued" || status == "running"
}

@Serializable
data class ServerDownloadRequest(@SerialName("version_id") val versionId: String, @SerialName("file_id") val fileId: String? = null)

@Serializable
data class ServerRetryRequest(@SerialName("file_id") val fileId: String? = null)

data class CatalogueQuery(
    val search: String = "",
    val console: String = "",
    val language: String = "",
    val installed: Boolean? = null,
    val source: String = "",
    val genre: String = "",
    val year: Int? = null,
    val sort: String = "recent",
    val page: Int = 1,
    val preferFrench: Boolean = true,
)
