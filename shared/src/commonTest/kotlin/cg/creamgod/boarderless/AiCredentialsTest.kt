package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.ai.*
import io.ktor.http.*
import kotlin.test.*

class AiCredentialsTest {
    @Test fun keyOrPastedBearerValueProducesExactlyOnePrefix() {
        for (input in listOf("fixture-key", "  fixture-key \n", "Bearer fixture-key", " bearer fixture-key ")) {
            val headers = aiCredentialHeaders(AiProbeAuthentication.Bearer, input)
            assertEquals(listOf("Bearer fixture-key"), headers.getAll(HttpHeaders.Authorization))
            assertEquals(AiPreparedAuthentication.Bearer, aiPreparedAuthentication(headers))
        }
        assertEquals("fixture-key", aiCredentialHeaders(AiProbeAuthentication.ApiKey, " fixture-key ")["x-api-key"])
        assertEquals(Headers.Empty, aiCredentialHeaders(AiProbeAuthentication.None, "unused private-key"))
    }
    @Test fun malformedKeysRejectWithoutExposingInput() {
        for (input in listOf("", "Bearer ", "Bearer Bearer private-key", "private-key\r\nx-api-key: other", "private-key secret", "private-key🙂", "x".repeat(8193))) {
            val failure = assertFailsWith<AiRequestValidationException> { aiCredentialHeaders(AiProbeAuthentication.Bearer, input) }
            assertFalse(failure.message.orEmpty().contains("private-key"))
        }
        assertFailsWith<AiRequestValidationException> { aiCredentialHeaders(AiProbeAuthentication.ApiKey, "Bearer private-key") }
    }
    @Test fun diagnosticTypesDiscardRealmErrorDescriptionsAndCredentialValues() {
        assertEquals(AiAuthenticationChallenge.Basic, aiAuthenticationChallenge(headersOf(HttpHeaders.WWWAuthenticate, "Basic realm=\"private-key\"")))
        assertEquals(AiAuthenticationChallenge.Bearer, aiAuthenticationChallenge(headersOf(HttpHeaders.WWWAuthenticate, "Bearer error_description=\"private-prompt\"")))
        assertEquals(AiAuthenticationChallenge.Other, aiAuthenticationChallenge(headersOf(HttpHeaders.WWWAuthenticate, "PrivateKeyAuth secret")))
        assertEquals(AiAuthenticationChallenge.Missing, aiAuthenticationChallenge(Headers.Empty))
        assertEquals(AiAuthenticationChallenge.Multiple, aiAuthenticationChallenge(Headers.build {
            append(HttpHeaders.WWWAuthenticate, "Basic realm=private-key"); append(HttpHeaders.WWWAuthenticate, "Bearer private-prompt")
        }))
        assertEquals(AiPreparedAuthentication.BearerAndApiKey, aiPreparedAuthentication(Headers.build {
            append(HttpHeaders.Authorization, "Bearer private-key"); append("x-api-key", "private-key")
        }))
    }
}
