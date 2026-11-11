package ovh.verbois.retrohub.emulation

import kotlin.math.min

/** Où placer l’image du jeu et les commandes tactiles, selon l’écran et la console. */
data class GameLayout(val game: NormRect, val controls: ControlsPlacement)

enum class ControlsPlacement {
    /** Pas de commandes à l’écran (manette branchée). */
    NONE,

    /** Portrait : les commandes occupent le bas de l’écran, sous le jeu. */
    BELOW,

    /** Paysage : commandes de part et d’autre du jeu. */
    SIDES,

    /** Paysage avec une image très large (DS côte à côte) : commandes dans les coins du bas. */
    BOTTOM_CORNERS,
}

object GameLayouts {
    /** Part minimale de l’écran réservée aux commandes en portrait. */
    const val PORTRAIT_CONTROLS = 0.38f

    /**
     * [aspect] est le rapport largeur/hauteur de l’image produite par le cœur (pour la DS, celui de
     * la disposition choisie). Les tailles sont en pixels, le résultat en coordonnées normalisées.
     */
    fun compute(
        width: Float,
        height: Float,
        aspect: Float,
        touchControls: Boolean,
        topInset: Float = 0f,
        /** Taille native (pixels carrés) : l’image est agrandie d’un nombre entier de fois. */
        native: Pair<Int, Int>? = null,
        portraitCentered: Boolean = false,
    ): GameLayout {
        val layout = place(width, height, aspect, touchControls, topInset, portraitCentered)
        if (native == null) return layout
        val (nw, nh) = native
        val gw = layout.game.width * width
        val gh = layout.game.height * height
        val factor = minOf(gw / nw, gh / nh).toInt()
        if (factor < 1) return layout
        val w = nw * factor / width
        val h = nh * factor / height
        val left = layout.game.left + (layout.game.width - w) / 2
        val top = if (layout.controls == ControlsPlacement.SIDES ||
            layout.controls == ControlsPlacement.NONE
        ) {
            layout.game.top + (layout.game.height - h) / 2
        } else {
            layout.game.top
        }
        return layout.copy(game = NormRect(left, top, w, h))
    }

    private fun place(width: Float, height: Float, aspect: Float, touchControls: Boolean, topInset: Float, portraitCentered: Boolean): GameLayout {
        val portrait = height > width
        if (!touchControls) return GameLayout(fit(width, height, 0f, 0f, width, height, aspect, center = true), ControlsPlacement.NONE)
        if (portrait) {
            val areaHeight = height * (1 - PORTRAIT_CONTROLS) - topInset
            return GameLayout(fit(width, height, 0f, topInset, width, areaHeight, aspect, center = portraitCentered), ControlsPlacement.BELOW)
        }
        // Paysage : on garde des colonnes latérales pour les pouces si l’image reste assez grande.
        val side = min(width * 0.21f, height * 0.5f)
        val sides = fit(width, height, side, 0f, width - 2 * side, height, aspect, center = true)
        if (sides.height >= 0.78f) return GameLayout(sides, ControlsPlacement.SIDES)
        // Image très large (DS côte à côte) : les écrans prennent le haut, presque toute la largeur,
        // et les commandes la bande du bas ; rien ne recouvre les écrans.
        val corners = fit(width, height, width * 0.07f, 0f, width * 0.86f, height * 0.66f, aspect, center = false)
        return GameLayout(corners, ControlsPlacement.BOTTOM_CORNERS)
    }

    /** Le plus grand rectangle de rapport [aspect] dans la zone donnée, centré horizontalement. */
    private fun fit(screenW: Float, screenH: Float, x: Float, y: Float, w: Float, h: Float, aspect: Float, center: Boolean): NormRect {
        var gw = w
        var gh = w / aspect
        if (gh > h) {
            gh = h
            gw = h * aspect
        }
        val left = x + (w - gw) / 2
        val top = if (center) y + (h - gh) / 2 else y
        return NormRect(left / screenW, top / screenH, gw / screenW, gh / screenH)
    }

    /** Rapport largeur/hauteur de l’image de la DS selon la disposition des écrans. */
    fun dsAspect(layout: DsLayout): Float = when (layout) {
        DsLayout.STACKED -> 256f / 384f

        DsLayout.SIDE_BY_SIDE -> 512f / 192f

        // Hybride (melonDS sur Android : rapport 2) : écran principal ×2 et l’autre en vignette à côté.
        DsLayout.MAIN_LARGE -> (256f * 2 + 256f) / (192f * 2)

        DsLayout.TOP_ONLY -> 256f / 192f
    }

    /** Disposition conseillée : l’un sous l’autre en portrait, côte à côte en paysage. */
    fun defaultDsLayout(portrait: Boolean): DsLayout = if (portrait) DsLayout.STACKED else DsLayout.SIDE_BY_SIDE
}
