package ovh.verbois.retrohub.ui.nav

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

sealed interface Route {
    data object Home : Route

    data class Library(val console: String? = null) : Route

    data class Game(val id: String) : Route

    data class Folder(val id: String) : Route

    data object Favorites : Route

    data object Downloads : Route

    data object Catalogue : Route

    data class CatalogueGame(val id: String) : Route

    data object Search : Route

    data object User : Route

    data class Conflict(val gameId: String) : Route

    data class Progression(val gameId: String) : Route

    data class Settings(val page: SettingsPage = SettingsPage.ROOT, val console: String? = null) : Route
}

enum class SettingsPage(val title: String) {
    ROOT("Paramètres"),

    // Compte RetroHub : partagé avec le web.
    ACCOUNT("Compte RetroHub"),
    SECURITY("Sécurité et appareils"),
    SYNC("Synchronisation"),

    // Cet appareil.
    APPEARANCE("Apparence"),
    DISPLAY("Affichage"),
    CONTROLS("Commandes"),
    AUDIO("Audio"),
    PERFORMANCE("Performances"),
    STORAGE("Téléchargements et stockage"),
    PARENTAL("Contrôle parental"),

    // Consoles et jeux.
    CONSOLES("Consoles"),
    CONSOLE("Console"),
    REMAP("Boutons de la manette"),
    GAME("Options du jeu"),

    ABOUT("À propos"),
    CHANGELOG("Nouveautés"),
}

/** Sections visibles, dans l’ordre des boutons LB / RB. La bibliothèque prolonge l’accueil. */
enum class Section(val title: String, val route: Route, val tab: Boolean = true) {
    HOME("Accueil", Route.Home),
    FAVORITES("Favoris", Route.Favorites),
    DOWNLOADS("Téléchargés", Route.Downloads),
    CATALOGUE("Catalogue", Route.Catalogue, tab = false),
    SEARCH("Rechercher", Route.Search, tab = false),
    USER("Profil", Route.User, tab = false),
    ;

    companion object {
        fun of(route: Route): Section? = when (route) {
            Route.Home, is Route.Game, is Route.Library, is Route.Folder, Route.Catalogue, is Route.CatalogueGame -> HOME
            Route.Favorites -> FAVORITES
            Route.Downloads -> DOWNLOADS
            Route.Search -> SEARCH
            Route.User -> USER
            else -> null
        }
    }
}

/** Pile d’écrans : une section à la racine, puis fiches, conflits et paramètres par-dessus. */
class Navigator(start: Route = Route.Home) {
    val stack = mutableStateListOf(start)
    val current: Route get() = stack.last()
    val contentRoute: Route get() = stack.lastOrNull { it != Route.User && it != Route.Search } ?: Route.Home

    /** Sens de la dernière transition, pour l’animation. */
    val forward = mutableStateOf(true)

    fun go(route: Route) {
        if (route == current) return
        forward.value = true
        stack.add(route)
    }

    fun section(section: Section) {
        if (section == Section.USER || section == Section.SEARCH) {
            while (stack.size > 1 && (current == Route.User || current == Route.Search)) stack.removeAt(stack.lastIndex)
            go(section.route)
            return
        }
        val from = Section.of(stack.first())
        forward.value = (from?.ordinal ?: 0) <= section.ordinal
        stack.clear()
        stack.add(section.route)
    }

    fun cycleSection(step: Int) {
        val tabs = Section.entries.filter { it.tab }
        val current = stack.asReversed().firstNotNullOfOrNull { route -> Section.of(route)?.takeIf { it.tab } } ?: Section.HOME
        val next = tabs[(tabs.indexOf(current) + step).coerceIn(0, tabs.lastIndex)]
        if (next != current) section(next)
    }

    /** Retour : écran précédent, puis l’accueil ; faux à l’accueil (la plateforme décide). */
    fun back(): Boolean {
        forward.value = false
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            return true
        }
        if (stack.first() != Route.Home) {
            stack[0] = Route.Home
            return true
        }
        return false
    }
}
