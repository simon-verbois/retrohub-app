package ovh.verbois.retrohub.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** The portrait (ui.mobile) and landscape (ui.console) interfaces never reach into each other (docs/ui-architecture.md). */
class InterfaceSeparationTest {
    private val root = File("src/commonMain/kotlin/ovh/verbois/retrohub/ui")
    private val entryPoints = setOf("RetroHubRoot.kt", "AppShell.kt")

    private fun violations(): List<String> {
        assertTrue(root.isDirectory, "UI sources not found from ${File(".").absolutePath}")
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { file ->
            val relative = file.relativeTo(root).invariantSeparatorsPath
            val forbidden = when {
                relative.startsWith("console/") -> listOf("ovh.verbois.retrohub.ui.mobile")
                relative.startsWith("mobile/") -> listOf("ovh.verbois.retrohub.ui.console")
                relative in entryPoints -> emptyList()
                else -> listOf("ovh.verbois.retrohub.ui.mobile", "ovh.verbois.retrohub.ui.console")
            }
            file.readLines().withIndex().flatMap { (index, line) ->
                forbidden.filter { line.contains(it) }.map { "$relative:${index + 1} uses $it" }
            }
        }.toList()
    }

    @Test
    fun mobileAndConsoleInterfacesOnlyShareCommonCode() {
        val found = violations()
        assertTrue(found.isEmpty(), "Interface separation broken:\n" + found.joinToString("\n"))
    }
}
