package ovh.verbois.retrohub.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import ovh.verbois.retrohub.TestApi
import ovh.verbois.retrohub.json
import ovh.verbois.retrohub.status
import kotlin.test.*

class ReportsTest {
    private class Capture(report: LocalReport) : ReportCapture {
        override val reports = MutableStateFlow(listOf(report))
        override suspend fun createReport(owner: String?) = reports.value.single()
        override fun update(id: String, change: (LocalReport) -> LocalReport) {
            reports.value = reports.value.map { if (it.id == id) change(it) else it }
        }
        override fun remove(id: String) {
            reports.value = reports.value.filterNot { it.id == id }
        }
    }
    private val diagnostic = EmulatorDiagnostic("report", "nes", "fceumm", "a".repeat(64), "Phone", "x86_64", "15")
    private fun report() = LocalReport("report", 1, "2026.47.1", "game_crash", owner = "owner", diagnostic = diagnostic, logs = "private local log")

    @Test fun consentIsRequiredAndLegacyDiagnosticsNeverReceiveLogsOrDescription() = runTest {
        val api = TestApi()
        try {
            val capture = Capture(report())
            val sender = ReportSender(capture, api.api) { "owner" }
            assertFalse(sender.flush())
            assertEquals(0, api.server.requestCount)
            sender.approve("report", listOf(FEATURE_DIAGNOSTICS), "My description")
            api.enqueue(json("{\"id\":\"report\"}", 201))
            assertFalse(sender.flush())
            val request = api.take()
            assertEquals("/api/v1/native/diagnostics", request.url.encodedPath)
            val body = request.body!!.utf8()
            assertTrue("\"consent\":true" in body)
            assertFalse("private local log" in body)
            assertFalse("My description" in body)
            assertTrue(capture.reports.value.single().sent)
        } finally {
            api.close()
        }
    }

    @Test fun retryKeepsTheApprovedBodyAndNeverSendsToAnotherAccount() = runTest {
        val api = TestApi()
        try {
            var owner = "owner"
            val capture = Capture(report())
            val sender = ReportSender(capture, api.api) { owner }
            sender.approve("report", listOf(FEATURE_BUG_REPORTS), "Description")
            val frozen = capture.reports.value.single().approved!!.body
            capture.update("report") { it.copy(description = "Changed later", logs = "Changed later") }
            owner = "other"
            assertFalse(sender.flush())
            assertEquals(0, api.server.requestCount)
            owner = "owner"
            api.enqueue(status(429))
            assertTrue(sender.flush())
            assertEquals(frozen, api.take().body!!.utf8())
            api.enqueue(json("{\"id\":\"report\"}", 201))
            assertFalse(sender.flush())
            assertEquals(frozen, api.take().body!!.utf8())
        } finally {
            api.close()
        }
    }

    @Test fun unsupportedOrOtherAccountReportsCannotBeApproved() {
        val api = TestApi()
        try {
            val capture = Capture(report())
            assertFailsWith<IllegalArgumentException> { ReportSender(capture, api.api) { "other" }.approve("report", listOf(FEATURE_DIAGNOSTICS), "") }
            assertFailsWith<IllegalStateException> { ReportSender(capture, api.api) { "owner" }.approve("report", emptyList(), "") }
            assertNull(capture.reports.value.single().approved)
        } finally {
            api.close()
        }
    }

    @Test fun exportedLogsStripCredentialsAddressesAndPathsAndStayBounded() {
        val filtered =
            sanitizeLog(
                "Authorization: Bearer device-secret\nhttps://private.example/api user@example.com /data/user/0/private/save.bin 192.168.1.42\n" + "x".repeat(
                    20000,
                ),
            )
        assertFalse("device-secret" in filtered)
        assertFalse("private.example" in filtered)
        assertFalse("user@example.com" in filtered)
        assertFalse("/data/user" in filtered)
        assertFalse("192.168.1.42" in filtered)
        assertTrue(filtered.length <= MAX_LOG_CHARS)
    }
}
