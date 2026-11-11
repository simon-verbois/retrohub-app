package ovh.verbois.retrohub.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ovh.verbois.retrohub.core.UpdateLevel
import ovh.verbois.retrohub.core.updateLevel
import ovh.verbois.retrohub.resources.Res
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.GroupDivider
import ovh.verbois.retrohub.ui.components.MessageBanner
import ovh.verbois.retrohub.ui.components.MessageKind
import ovh.verbois.retrohub.ui.components.NavigationRow
import ovh.verbois.retrohub.ui.components.PrimaryButton
import ovh.verbois.retrohub.ui.components.SettingRow
import ovh.verbois.retrohub.ui.components.SettingsGroup
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.nav.SettingsPage
import ovh.verbois.retrohub.ui.theme.Palette

@Composable
internal fun AboutPage(controller: UiController) {
    val container = controller.container
    val platform = container.platform
    val status by container.serverStatus.collectAsState()
    val sync by container.sync.status.collectAsState()
    val android = status?.apps?.android
    val level = updateLevel(platform.appVersion, android?.latestVersion, android?.minVersion)
    if (level != UpdateLevel.NONE) {
        MessageBanner(
            if (level == UpdateLevel.REQUIRED) {
                "Une mise à jour est nécessaire pour continuer à synchroniser avec ce serveur (version ${android?.latestVersion ?: android?.minVersion})."
            } else {
                "La version ${android?.latestVersion} est disponible."
            },
            if (level == UpdateLevel.REQUIRED) MessageKind.WARNING else MessageKind.INFO,
        ) {
            android?.downloadUrl?.let { url ->
                PrimaryButton("Télécharger", { platform.openUrl(container.api.resolve(url)) }, icon = Lucide.Download, height = 44.dp)
            }
        }
    }
    SettingsGroup {
        SettingRow(
            "RetroHub",
            subtitle = "Version ${platform.appVersion}" + if (level == UpdateLevel.NONE && android?.latestVersion != null) " · à jour" else "",
            icon = Lucide.Gamepad2,
        )
        GroupDivider()
        NavigationRow("Nouveautés", Lucide.Sparkles, { controller.nav.go(Route.Settings(SettingsPage.CHANGELOG)) })
        GroupDivider()
        SettingRow(
            "Signaler un problème",
            icon = Lucide.Share2,
            onClick = {
                val account = container.account.account
                platform.shareLogs(
                    buildString {
                        appendLine("RetroHub ${platform.appVersion}")
                        account?.let { appendLine("Serveur : ${it.serverName} ${it.serverVersion}") }
                        appendLine(
                            "Synchronisation : ${sync.phase}, ${sync.pendingSaves} sauvegarde(s) et ${sync.pendingSessions} session(s) en attente, ${sync.conflicts} conflit(s)",
                        )
                        appendLine("Émulateurs : ${platform.availableCores().sorted().joinToString()}")
                    },
                )
            },
        )
        GroupDivider()
        SettingRow(
            "Licences",
            subtitle = "Moteur LibretroDroid et cœurs libretro (GPL et licences libres). Icônes Lucide (ISC), police Manrope (OFL).",
            icon = Lucide.FileText,
        )
        GroupDivider()
        SettingRow(
            "Émulateurs embarqués",
            subtitle = platform.availableCores().sorted().joinToString(", ") { coreLabel(it) }.ifEmpty { "aucun" },
            icon = Lucide.Layers,
        )
    }
}

/** Une version du CHANGELOG : titre et puces par section. */
data class ChangelogEntry(val version: String, val sections: List<Pair<String, List<String>>>)

/** Lecture du CHANGELOG.md (format Keep a Changelog, en français), sans les commentaires. */
fun parseChangelog(markdown: String): List<ChangelogEntry> {
    val text = markdown.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
    val entries = mutableListOf<ChangelogEntry>()
    var version: String? = null
    var sections = mutableListOf<Pair<String, MutableList<String>>>()
    fun flush() {
        version?.let { v -> entries += ChangelogEntry(v, sections.filter { it.second.isNotEmpty() && it.first != "Internal" }) }
    }
    for (line in text.lines()) {
        when {
            line.startsWith("## ") -> {
                flush()
                version = line.removePrefix("## ").trim().replace("] - ", " · ").replace("[", "").replace("]", "")
                sections = mutableListOf()
            }

            line.startsWith("### ") -> sections += line.removePrefix("### ").trim() to mutableListOf()

            line.startsWith("- ") && sections.isNotEmpty() -> sections.last().second += line.removePrefix("- ").trim()

            line.startsWith("  ") && line.isNotBlank() && sections.isNotEmpty() && sections.last().second.isNotEmpty() -> {
                val items = sections.last().second
                items[items.lastIndex] = items.last() + " " + line.trim()
            }
        }
    }
    flush()
    return entries.filter { it.sections.isNotEmpty() }
}

suspend fun loadChangelog(): List<ChangelogEntry> = runCatching {
    parseChangelog(Res.readBytes("files/CHANGELOG.md").decodeToString())
}.getOrDefault(emptyList())

@Composable
internal fun ChangelogPage() {
    val entries by produceState<List<ChangelogEntry>?>(null) { value = loadChangelog() }
    entries?.let { ChangelogContent(it) }
}

@Composable
fun ChangelogContent(entries: List<ChangelogEntry>, limit: Int = Int.MAX_VALUE) {
    val shown = remember(entries, limit) { entries.take(limit) }
    shown.forEach { entry ->
        Text(if (entry.version == "Unreleased") "Prochaine version" else entry.version, style = MaterialTheme.typography.titleLarge)
        entry.sections.forEach { (title, items) ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = Palette.TextDim)
                items.forEach { item ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("•", style = MaterialTheme.typography.bodyMedium)
                        Text(item, Modifier.padding(end = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
