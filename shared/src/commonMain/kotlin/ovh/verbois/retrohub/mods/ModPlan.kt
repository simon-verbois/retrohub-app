package ovh.verbois.retrohub.mods

import okio.Buffer
import ovh.verbois.retrohub.api.ModRelease
import ovh.verbois.retrohub.api.ModSelection
import ovh.verbois.retrohub.core.sha256
import ovh.verbois.retrohub.game.Cheat

class ModProblem(message: String) : Exception(message)

data class ModEnvironment(val console: String, val core: String, val build: String?, val titleId: String?, val romSha: String)

/** Normalized destinations are owned by installers, never supplied by the server. */
data class ModPlan(
    val releases: List<ModRelease>,
    val fingerprint: String?,
    val gameplayFingerprint: String?,
    val files: List<ModPlannedFile>,
    val cheats: List<Cheat>,
    val titleId: String?,
)
data class ModPlannedFile(val artifact: String, val source: String, val destination: String)

object ModPlanner {
    const val TEXTURES = "azahar_textures_v1"
    const val ROMFS = "azahar_romfs_v1"
    const val EXEFS = "azahar_exefs_v1"
    const val CHEATS = "azahar_cheats_v1"
    val installers = setOf(TEXTURES, ROMFS, EXEFS, CHEATS)
    const val MAX_OBJECT = 8L * 1024 * 1024 * 1024
    const val MAX_EXPANDED = 16L * 1024 * 1024 * 1024
    const val MAX_FILES = 100_000

    fun compatibility(release: ModRelease, environment: ModEnvironment): String? = try {
        validate(release, environment)
        null
    } catch (error: ModProblem) {
        error.message
    }

    fun plan(selections: List<ModSelection>, available: List<ModRelease>, environment: ModEnvironment): ModPlan {
        check(selections.size <= 64 && selections.distinctBy { it.id }.size == selections.size, "Choisissez une seule version de chaque mod.")
        val chosen = selections.sortedBy { it.id }.map { selection ->
            available.singleOrNull { it.selection == selection } ?: throw ModProblem("Une version du mod manque. Actualisez la liste des mods.")
        }
        chosen.forEach { validate(it, environment) }
        val ordered = mutableListOf<ModRelease>()
        val visiting = mutableSetOf<ModSelection>()
        fun visit(release: ModRelease) {
            if (release in ordered) return
            check(visiting.add(release.selection), "Ces mods dépendent les uns des autres. Choisissez une autre combinaison.")
            check(chosen.none { it.selection.id in release.conflicts }, "Ces mods ne peuvent pas être activés ensemble.")
            for (required in release.requires) {
                val dependency = chosen.singleOrNull { it.selection == required }
                    ?: throw ModProblem("Installez aussi les mods nécessaires à « ${release.name} ».")
                visit(dependency)
            }
            visiting.remove(release.selection)
            ordered += release
        }
        chosen.forEach(::visit)
        val title = environment.titleId.orEmpty().uppercase()
        val files = ordered.flatMap { release ->
            release.components.flatMap { component ->
                val prefix = when (component.installer) {
                    TEXTURES -> "textures/$title/"
                    ROMFS -> "mods/$title/romfs/"
                    EXEFS -> "mods/$title/exefs/"
                    else -> ""
                }
                component.artifact?.let { artifact ->
                    artifact.files.map {
                        // Azahar reads the extended header beside exefs/, not inside it.
                        val destination = if (component.installer == EXEFS && it.path == "exheader.bin") "mods/$title/exheader.bin" else prefix + it.path
                        ModPlannedFile(artifact.sha256.lowercase(), it.path, destination)
                    }
                }
                    ?: emptyList()
            }
        }
        check(
            files.size <= MAX_FILES && files.distinctBy {
                it.destination.lowercase()
            }.size == files.size,
            "Plusieurs mods remplacent le même fichier. Désactivez-en un.",
        )
        // The core has aliases for these files. A code replacement and a code patch are exclusive.
        val destinations = files.map { it.destination.lowercase() }.toSet()
        for (file in destinations) {
            var parent = file.substringBeforeLast('/', "")
            while (parent.isNotEmpty()) {
                check(parent !in destinations, "Plusieurs mods remplacent le même dossier. Désactivez-en un.")
                parent = parent.substringBeforeLast('/', "")
            }
        }
        val executable = files.filter { it.destination.startsWith("mods/$title/exefs/code.") }
        check(executable.size <= 1, "Plusieurs mods modifient le programme du jeu. Désactivez-en un.")
        // The extended header describes the program's memory layout: it must come with the code change
        // it was made for, never meet another mod's code.
        val program = ordered.filter { release ->
            release.components.any { component ->
                component.installer == EXEFS && component.artifact?.files.orEmpty().any { it.path == "exheader.bin" || it.path.startsWith("code.") }
            }
        }
        check(program.size <= 1, "Plusieurs mods modifient le programme du jeu. Désactivez-en un.")
        check(
            ordered.flatMap { it.components }.mapNotNull { it.artifact }.distinctBy { it.sha256 }.sumOf { it.files.sumOf { f -> f.size } } <= MAX_EXPANDED,
            "Ces mods prennent trop de place. Choisissez une combinaison plus légère.",
        )
        return ModPlan(
            ordered,
            fingerprint(ordered, false),
            fingerprint(ordered, true),
            files,
            ordered.flatMap {
                it.components
            }.flatMap { it.cheats },
            environment.titleId?.takeIf { Regex("00040000[0-9a-fA-F]{8}").matches(it) },
        )
    }

    private fun validate(release: ModRelease, environment: ModEnvironment) {
        check(hash(environment.romSha), "Téléchargez une version compatible du jeu avant d’installer ce mod.")
        check(
            environment.console == "3ds" && environment.core == "azahar" && release.console == environment.console && release.core == environment.core,
            "Ce mod ne fonctionne pas avec l’émulateur de cet appareil.",
        )
        check(
            environment.titleId != null && Regex("00040000[0-9a-fA-F]{8}").matches(environment.titleId) &&
                release.titleIds.any { it.equals(environment.titleId, true) },
            "Ce mod ne correspond pas à cette édition du jeu.",
        )
        check(release.coreBuilds.isEmpty() || environment.build in release.coreBuilds, "Mettez l’application à jour pour utiliser ce mod.")
        check(release.romSha256.isEmpty() || release.romSha256.any { it.equals(environment.romSha, true) }, "Ce mod demande une autre version du jeu.")
        check(
            listOf(release.selection.id, release.selection.version, release.selection.variant).all {
                Regex("[A-Za-z0-9_.:+-]{1,200}").matches(it)
            },
            "La description du mod est invalide. Actualisez la liste.",
        )
        check(release.components.isNotEmpty() && release.components.size <= 16, "Ce mod est incomplet. Actualisez la liste.")
        val gameplay = release.components.any { it.installer != TEXTURES }
        check(!gameplay || release.romSha256.isNotEmpty(), "La compatibilité de ce mod avec votre version du jeu n’est pas précisée.")
        for (component in release.components) {
            check(component.installer in installers, "Ce type de mod n’est pas encore pris en charge sur cet appareil.")
            if (component.installer == CHEATS) {
                check(
                    component.artifact == null && component.cheats.isNotEmpty() && component.cheats.size <= 256 &&
                        component.cheats.all {
                            it.code.length in 1..16_384 && it.name.length <= 200 &&
                                it.name.none { c -> c == '[' || c == ']' || c.code < 32 } &&
                                it.code.lines().all { line -> Regex("[0-9a-fA-F]{8} [0-9a-fA-F]{8}").matches(line) }
                        },
                    "Les codes du mod sont invalides.",
                )
            } else {
                check(component.cheats.isEmpty(), "La description du mod est invalide.")
                val artifact = component.artifact ?: throw ModProblem("Les fichiers du mod manquent.")
                check(hash(artifact.sha256) && artifact.size in 1..MAX_OBJECT && artifact.files.size in 1..MAX_FILES, "Les fichiers du mod sont invalides.")
                check(artifact.url == "/api/v1/native/mods/objects/${artifact.sha256.lowercase()}", "L’adresse de téléchargement du mod est invalide.")
                check(artifact.files.none { it.path.substringBefore('/') == ".complete" }, "Les fichiers du mod sont invalides.")
                check(
                    artifact.files.all { safePath(it.path) && hash(it.sha256) && it.size in 0..MAX_EXPANDED } &&
                        artifact.files.sumOf { it.size } in 1..MAX_EXPANDED &&
                        artifact.files.distinctBy { it.path.lowercase() }.size == artifact.files.size,
                    "Les fichiers du mod sont invalides.",
                )
                if (component.installer ==
                    TEXTURES
                ) {
                    check(
                        artifact.files.all {
                            it.path.substringAfterLast('.').lowercase() in setOf("png", "dds", "ktx", "json")
                        },
                        "Ce pack contient des textures non prises en charge.",
                    )
                }
                if (component.installer ==
                    EXEFS
                ) {
                    check(
                        artifact.files.all {
                            it.path in setOf("code.ips", "code.bps", "code.bin", "exheader.bin", "icon.bin", "banner.bin", "logo.bin")
                        },
                        "Ce correctif contient des fichiers non pris en charge.",
                    )
                }
            }
        }
    }

    /** Length-prefixed UTF-8 records; the server implements this same versioned algorithm. */
    fun fingerprint(releases: List<ModRelease>, gameplayOnly: Boolean): String? {
        val selected = releases.map { it to it.components.filter { c -> !gameplayOnly || c.installer != TEXTURES } }.filter { it.second.isNotEmpty() }
        if (selected.isEmpty()) return null
        val data = Buffer()
        fun add(value: String) {
            val bytes = value.encodeToByteArray()
            data.writeInt(bytes.size)
            data.write(bytes)
        }
        add(if (gameplayOnly) "retrohub-gameplay-v1" else "retrohub-mods-v1")
        add(selected.size.toString())
        for ((release, components) in selected) {
            add(release.selection.id)
            add(release.selection.version)
            if (!gameplayOnly) add(release.selection.variant)
            add(components.size.toString())
            for (component in components) {
                add(component.installer)
                add(component.artifact?.sha256?.lowercase().orEmpty())
                add(component.cheats.size.toString())
                component.cheats.forEach {
                    add(it.code)
                    add(it.enabled.toString())
                }
            }
        }
        return sha256(data.readByteArray())
    }

    fun hash(value: String) = Regex("[a-fA-F0-9]{64}").matches(value)
    fun safePath(value: String) = value.length in 1..512 && !value.startsWith('/') && value.none { it == '\\' || it == ':' || it.code < 32 } &&
        value.split('/').all { it.isNotEmpty() && it != "." && it != ".." }
    private fun check(condition: Boolean, message: String) {
        if (!condition) throw ModProblem(message)
    }
}
