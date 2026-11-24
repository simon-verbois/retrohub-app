package ovh.verbois.retrohub.ui.play

/**
 * French labels for the options the bundled cores expose in English. Unknown labels are made readable
 * (no underscores, sentence case) instead of being shown raw.
 */
object CoreOptionText {
    private val labels = mapOf(
        "aspect ratio" to "Format de l’image",
        "ntsc filter" to "Filtre NTSC",
        "color palette" to "Palette de couleurs",
        "palette" to "Palette de couleurs",
        "region" to "Région",
        "system region" to "Région de la console",
        "console region" to "Région de la console",
        "language" to "Langue",
        "system language" to "Langue de la console",
        "internal resolution" to "Résolution interne",
        "rendering resolution" to "Résolution de rendu",
        "resolution" to "Résolution",
        "frameskip" to "Saut d’images",
        "frame skip" to "Saut d’images",
        "overclock" to "Accélération du processeur",
        "cpu overclock" to "Accélération du processeur",
        "crop overscan" to "Rogner les bords",
        "overscan" to "Bords de l’image",
        "sound quality" to "Qualité du son",
        "audio quality" to "Qualité du son",
        "sound interpolation" to "Lissage du son",
        "audio interpolation" to "Lissage du son",
        "turbo" to "Turbo",
        "turbo enable" to "Turbo",
        "no sprite limit" to "Sans limite de sprites",
        "sprite limit" to "Limite de sprites",
        "widescreen hack" to "Écran large (astuce)",
        "widescreen" to "Écran large",
        "texture filtering" to "Filtrage des textures",
        "anisotropic filtering" to "Filtrage anisotrope",
        "anti-aliasing" to "Anticrénelage",
        "dithering" to "Tramage",
        "vsync" to "Synchronisation verticale",
        "show lag frames" to "Afficher les images perdues",
        "bios" to "BIOS",
        "boot logo" to "Logo de démarrage",
        "skip bios" to "Passer le BIOS",
        "memory card" to "Carte mémoire",
        "rumble" to "Vibrations",
        "analog" to "Analogique",
        "screen layout" to "Disposition des écrans",
        "screen gap" to "Espace entre les écrans",
        "renderer" to "Moteur de rendu",
        "game speed" to "Vitesse du jeu",
        "zapper" to "Pistolet Zapper",
    )

    private val values = mapOf(
        "enabled" to "Activé",
        "disabled" to "Désactivé",
        "on" to "Activé",
        "off" to "Désactivé",
        "true" to "Activé",
        "false" to "Désactivé",
        "default" to "Par défaut",
        "auto" to "Automatique",
        "automatic" to "Automatique",
        "none" to "Aucun",
        "low" to "Basse",
        "medium" to "Moyenne",
        "high" to "Haute",
        "native" to "Native",
        "japan" to "Japon",
        "europe" to "Europe",
        "usa" to "États-Unis",
        "us" to "États-Unis",
        "english" to "Anglais",
        "french" to "Français",
        "japanese" to "Japonais",
        "german" to "Allemand",
        "spanish" to "Espagnol",
        "italian" to "Italien",
        "linear" to "Linéaire",
        "nearest" to "Pixels nets",
        "bilinear" to "Bilinéaire",
        "composite" to "Composite",
        "rgb" to "RVB",
        "s-video" to "S-Vidéo",
        "monochrome" to "Monochrome",
        "fast" to "Rapide",
        "accurate" to "Précis",
    )

    fun label(key: String, label: String): String {
        val raw = label.ifBlank { key }
        val normalized = raw.trim().lowercase().replace('_', ' ').replace(Regex("\\s+"), " ")
        labels[normalized]?.let { return it }
        // Core prefixes such as « fceumm_ » or « Core: » add nothing for the player.
        val stripped = normalized.substringAfter(": ").replace(Regex("^(fceumm|snes9x|gambatte|mgba|genesis plus gx|melonds|ppsspp|flycast|dolphin) "), "")
        labels[stripped]?.let { return it }
        return readable(if (label.isBlank()) stripped else raw)
    }

    fun value(value: String): String {
        val normalized = value.trim().lowercase()
        values[normalized]?.let { return it }
        return if (normalized.contains('_') || normalized == value.trim()) readable(value) else value
    }

    private fun readable(text: String): String {
        val spaced = text.trim().replace('_', ' ').replace(Regex("\\s+"), " ")
        return spaced.replaceFirstChar { it.uppercase() }
    }
}
