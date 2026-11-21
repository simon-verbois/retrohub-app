package ovh.verbois.retrohub.ui.home

import ovh.verbois.retrohub.core.parseInstant
import ovh.verbois.retrohub.emulation.AppSettings
import ovh.verbois.retrohub.emulation.HomeOrder
import ovh.verbois.retrohub.library.Game
import kotlin.time.Instant

/** Only preferences that change the home model invalidate its background computation. */
data class HomePreferences(
    val order: HomeOrder = HomeOrder.RECENT,
    val hidden: Set<String> = emptySet(),
    val downloadedOnly: Boolean = false,
    val activity: Map<String, String> = emptyMap(),
) {
    constructor(settings: AppSettings) : this(settings.homeOrder, settings.hiddenConsoles, settings.offlineOnly, settings.homeActivity)
}

/** Indexes are built once with the library, never while handling a direction or confirmation. */
class HomeColumn(val key: String, val title: String, val games: List<Game>, val console: String? = null) {
    val indices: Map<String, Int> = games.mapIndexed { index, game -> game.id to index }.toMap()
}

class HomeLibrary(val games: List<Game>, val columns: List<HomeColumn>) {
    val byId = games.associateBy { it.id }
    val byColumn = columns.associateBy { it.key }
    val columnIndices = columns.mapIndexed { index, column -> column.key to index }.toMap()

    companion object {
        val Empty = homeLibrary(emptyList(), HomePreferences())
    }
}

data class HomeSelection(val column: HomeColumn, val categoryIndex: Int, val gameIndex: Int) {
    val game: Game? get() = column.games.getOrNull(gameIndex)
}

fun homeLibrary(games: List<Game>, preferences: HomePreferences): HomeLibrary {
    val ordered = homeOrder(games, preferences.order, preferences.hidden, preferences.downloadedOnly, preferences.activity)
    val consoles = ordered.groupBy { it.console }.map { (console, items) ->
        HomeColumn("console:$console", ovh.verbois.retrohub.emulation.Consoles.name(console), items, console)
    }.sortedBy { it.title }
    return HomeLibrary(
        ordered,
        listOf(HomeColumn("catalogue", "Catalogue", emptyList()), HomeColumn("favorites", "Favoris", ordered.filter { it.favorite })) + consoles,
    )
}

fun homeColumns(games: List<Game>, settings: AppSettings): List<HomeColumn> = homeLibrary(games, HomePreferences(settings)).columns

/** Downloads and offline play count as activity. Parse timestamps once, before sorting. */
fun homeOrder(
    games: List<Game>,
    order: HomeOrder = HomeOrder.RECENT,
    hidden: Set<String> = emptySet(),
    downloadedOnly: Boolean = false,
    activity: Map<String, String> = emptyMap(),
): List<Game> {
    val shown = games.filter { it.console !in hidden && (!downloadedOnly || it.downloaded) }
    if (order == HomeOrder.TITLE) return shown.sortedBy { it.sortTitle }
    val dates = shown.associate { game ->
        game.id to
            maxOf(
                game.lastPlayed ?: Instant.DISTANT_PAST,
                parseInstant(game.local?.downloadedAt) ?: Instant.DISTANT_PAST,
                parseInstant(activity[game.id]) ?: Instant.DISTANT_PAST,
            )
    }
    val recent = compareByDescending<Game> { dates.getValue(it.id) }.thenBy { it.sortTitle }
    return shown.sortedWith(if (order == HomeOrder.FAVORITES) compareByDescending<Game> { it.favorite }.then(recent) else recent)
}
