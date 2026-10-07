package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.remote.configuredBackendBaseUrl
import kotlin.test.Test
import kotlin.test.assertEquals

class BackendBaseUrlTest {
    private val fallback = "http://127.0.0.1:3000"

    @Test
    fun trimsWhitespaceAndTrailingSlashes() {
        assertEquals(
            "https://boarderless.example.test/api",
            configuredBackendBaseUrl("  https://boarderless.example.test/api///  ", fallback),
        )
    }

    @Test
    fun acceptsPhysicalDeviceLanAddress() {
        assertEquals(
            "http://192.168.1.42:3000",
            configuredBackendBaseUrl("http://192.168.1.42:3000", fallback),
        )
    }

    @Test
    fun fallsBackForMissingInvalidOrUnexpandedConfiguration() {
        assertEquals(fallback, configuredBackendBaseUrl(null, fallback))
        assertEquals(fallback, configuredBackendBaseUrl("ftp://example.test", fallback))
        assertEquals(fallback, configuredBackendBaseUrl("http://", fallback))
        assertEquals(fallback, configuredBackendBaseUrl("$(BOARDERLESS_BACKEND_URL)", fallback))
    }
}
