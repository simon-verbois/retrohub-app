package ovh.verbois.retrohub.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PairingQrTest {
    @Test
    fun acceptsTheServersOneUseCodeWithCommonSeparators() {
        assertEquals("ABCD-EFGH", pairingCodeFromQr("abcd-efgh"))
        assertEquals("ABCD-EFGH", pairingCodeFromQr("  ABCD EFGH  "))
        assertEquals("ABCD-EFGH", pairingCodeFromQr("ABCDEFGH"))
    }

    @Test
    fun doesNotInterpretUrlsTokensOrArbitraryCameraPayloadsAsCredentials() {
        listOf("", "ABCDE", "ABC0-EFGH", "ABCD-EFGI", "https://example.test/?code=ABCD-EFGH", "Bearer ABCDEFGH", "A".repeat(2048)).forEach {
            assertNull(pairingCodeFromQr(it))
        }
    }
}
