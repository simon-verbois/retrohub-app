package ovh.verbois.retrohub.ui.home

import ovh.verbois.retrohub.core.parseInstant
import ovh.verbois.retrohub.emulation.AppSettings
import ovh.verbois.retrohub.emulation.HomeFolder
import ovh.verbois.retrohub.emulation.HomeOrder
import ovh.verbois.retrohub.library.Game

/** Catalogue and Favorites lead the horizontal carousel, followed by console libraries. */
data class HomeColumn(val key: String, val title: String, val games: List<Game>, val console: String? = null)

fun homeColumns(games: List<Game>, settings: AppSettings): List<HomeColumn> {
    val ordered = homeOrder(games, settings.homeOrder, settings.hiddenConsoles, settings.offlineOnly, settings.homeActivity)
    val consoles = ordered.groupBy { it.console }.map { (console, items) ->
        HomeColumn("console:$console", ovh.verbois.retrohub.emulation.Consoles.name(console), items, console)
    }.sortedBy { it.title }
    return listOf(
        HomeColumn("catalogue", "Catalogue", emptyList()),
        HomeColumn("favorites", "Favoris", ordered.filter { it.favorite }),
    ) + consoles
}

sealed interface HomeEntry {
    val key: String

    data object Catalogue : HomeEntry {
        override val key = "catalogue"
    }
    data class GameItem(val game: Game) : HomeEntry {
        override val key get() = game.id
    }
    data class FolderItem(val folder: HomeFolder) : HomeEntry {
        override val key get() = folder.key
    }
    data object Library : HomeEntry {
        override val key = "library"
    }
    data object NewFolder : HomeEntry {
        override val key = "new-folder"
    }
}

/** Downloads and local play count as activity even before a session reaches the server. */
fun homeOrder(
    games: List<Game>,
    order: HomeOrder = HomeOrder.RECENT,
    hidden: Set<String> = emptySet(),
    downloadedOnly: Boolean = false,
    activity: Map<String, String> = emptyMap(),
): List<Game> {
    val shown = games.filter { it.console !in hidden && (!downloadedOnly || it.downloaded) }
    val recent = compareByDescending<Game> { game ->
        listOfNotNull(game.lastPlayed, parseInstant(game.local?.downloadedAt), parseInstant(activity[game.id])).maxOrNull()
    }.thenBy { it.sortTitle }
    return when (order) {
        HomeOrder.RECENT -> shown.sortedWith(recent)
        HomeOrder.TITLE -> shown.sortedBy { it.sortTitle }
        HomeOrder.FAVORITES -> shown.sortedWith(compareByDescending<Game> { it.favorite }.then(recent))
    }
}

fun homeEntries(games: List<Game>, settings: AppSettings, selected: String?): List<HomeEntry> {
    val ordered = homeOrder(games, settings.homeOrder, settings.hiddenConsoles, settings.offlineOnly, settings.homeActivity)
    val automatic = ordered.groupBy { it.console }.map { (console, games) ->
        HomeEntry.FolderItem(
            HomeFolder(
                "console:$console",
                ovh.verbois.retrohub.emulation.Consoles.name(console),
                games.map { it.id },
                console in settings.homeConsoleFavorites,
            ),
        )
    }
    val available = automatic + ordered.filter { it.id in settings.homePins }.map { HomeEntry.GameItem(it) }
    val byKey = available.associateBy { it.key }
    val pins = settings.homePins.distinct().mapNotNull(byKey::get)
    return listOf(HomeEntry.Catalogue) + (pins + automatic).distinctBy { it.key } + HomeEntry.Library
}
