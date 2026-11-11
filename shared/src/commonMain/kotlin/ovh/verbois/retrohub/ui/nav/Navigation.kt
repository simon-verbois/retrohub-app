package ovh.verbois.retrohub.ui.nav

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

sealed interface Route {
    data object Home : Route

    data class Library(val console: String? = null) : Route

    data object Favorites : Route

    data object Downloads : Route

    data object Search : Route

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

/**
 * Les sections, parcourues avec LB / RB comme sur une console. Seules Accueil et Téléchargés sont
 * des onglets : la bibliothèque s’ouvre par la tuile en fin de rangée, les favoris par le cœur.
 */
enum class Section(val title: String, val route: Route, val tab: Boolean = true) {
    HOME("Accueil", Route.Home),
    LIBRARY("Bibliothèque", Route.Library(), tab = false),
    FAVORITES("Favoris", Route.Favorites, tab = false),
    DOWNLOADS("Téléchargés", Route.Downloads),
    ;

    companion object {
        fun of(route: Route): Section? = when (route) {
            Route.Home -> HOME
            is Route.Library -> LIBRARY
            Route.Favorites -> FAVORITES
            Route.Downloads -> DOWNLOADS
            else -> null
        }
    }
}

/** Pile d’écrans : une section à la racine, puis fiches, conflits et paramètres par-dessus. */
class Navigator(start: Route = Route.Home) {
    val stack = mutableStateListOf(start)
    val current: Route get() = stack.last()

    /** Sens de la dernière transition, pour l’animation. */
    val forward = mutableStateOf(true)

    fun go(route: Route) {
        if (route == current) return
        forward.value = true
        stack.add(route)
    }

    fun replace(route: Route) {
        forward.value = true
        stack[stack.lastIndex] = route
    }

    fun section(section: Section) {
        val from = Section.of(stack.first())
        forward.value = (from?.ordinal ?: 0) <= section.ordinal
        stack.clear()
        stack.add(section.route)
    }

    fun cycleSection(step: Int) {
        val current = Section.of(stack.first()) ?: Section.HOME
        val entries = Section.entries
        section(entries[(current.ordinal + step + entries.size) % entries.size])
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
