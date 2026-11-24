package ovh.verbois.retrohub.ui.catalogue

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import ovh.verbois.retrohub.AppContainer
import ovh.verbois.retrohub.api.ApiException
import ovh.verbois.retrohub.api.CatalogueGame
import ovh.verbois.retrohub.api.CatalogueQuery

/** Catalogue filters, independent of paging (saved with the screen). */
@Serializable
data class CatalogueFilters(
    val search: String = "",
    val console: String = "",
    val language: String = "",
    val installed: Boolean? = null,
    val source: String = "",
    val genre: String = "",
    val year: Int? = null,
    val sort: String = "recent",
) {
    val active: Boolean get() = console.isNotBlank() || language.isNotBlank() || source.isNotBlank() || genre.isNotBlank() || year != null

    fun query(page: Int, preferFrench: Boolean) = CatalogueQuery(search, console, language, installed, source, genre, year, sort, page, preferFrench)
}

/** Server catalogue pages, appended as the player scrolls. */
@Stable
class CatalogueFeed {
    val items = mutableStateListOf<CatalogueGame>()
    var total by mutableIntStateOf(0)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** True once the first page answered, even with no game. */
    var loaded by mutableStateOf(false)
        private set
    private var page = 0
    private var wanted by mutableIntStateOf(1)

    /** Asks for the next page when more games exist. */
    fun more() {
        if (!loading && error == null && items.size < total && wanted <= page) wanted = page + 1
    }

    internal fun reset() {
        items.clear()
        total = 0
        page = 0
        wanted = 1
        error = null
        loaded = false
    }

    internal suspend fun load(container: AppContainer, filters: CatalogueFilters, number: Int) {
        if (number <= page) return
        loading = true
        error = null
        try {
            val result = container.api.catalogue(filters.query(number, container.settings.settings.value.preferFrench))
            val known = items.mapTo(HashSet()) { it.id }
            items.addAll(result.items.filter { it.id !in known })
            total = result.total
            page = number
            loaded = true
        } catch (failure: ApiException) {
            error = failure.message ?: "Le catalogue est indisponible. Réessayez."
        } finally {
            loading = false
        }
    }

    internal val requested: Int get() = wanted
}

/** Loads [filters] from the first page; [refresh] reloads everything. */
@Composable
fun rememberCatalogueFeed(container: AppContainer, filters: CatalogueFilters, online: Boolean, refresh: Int): CatalogueFeed {
    val feed = remember { CatalogueFeed() }
    LaunchedEffect(filters, online, refresh) {
        feed.reset()
        if (!online) return@LaunchedEffect
        delay(250)
        snapshotFlow { feed.requested }.collect { feed.load(container, filters, it) }
    }
    return feed
}
