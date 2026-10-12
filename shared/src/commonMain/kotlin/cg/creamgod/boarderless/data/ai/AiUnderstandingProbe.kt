package cg.creamgod.boarderless.data.ai

import cg.creamgod.boarderless.data.remote.randomUuid
import cg.creamgod.boarderless.domain.ai.*
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.Headers
import kotlinx.coroutines.flow.collect

/** Read-only, one explicit approved prompt; no tool executor or WorkspaceRepository mutation. */
internal suspend fun runAiUnderstandingProbe(
    server: AiServerProfile,
    headers: Headers,
    prompt: String,
    approved: suspend () -> Boolean,
    onText: (String) -> Unit,
    client: HttpClient =
        cg.creamgod.boarderless.data.platformHttpClient {
            followRedirects = false
            install(HttpTimeout)
        },
    onDiagnostic: (AiProbeDiagnostic) -> Unit = {},
): String {
    var last: AiProbeDiagnostic? = null
    var response: AiProbeDiagnostic? = null

    fun record(event: AiProbeDiagnostic) {
        if (last?.stage == AiProbeStage.Failed) return
        if (event.stage == AiProbeStage.HttpResponse) response = event
        val safe =
            if (event.stage == AiProbeStage.Failed) {
                event.copy(
                    httpStatus = event.httpStatus ?: response?.httpStatus,
                    responseType = event.responseType ?: response?.responseType,
                    preparedAuthentication = event.preparedAuthentication ?: response?.preparedAuthentication,
                    authenticationChallenge = event.authenticationChallenge ?: response?.authenticationChallenge,
                    responseRequestId = event.responseRequestId ?: response?.responseRequestId,
                    responseHeadersMillis = event.responseHeadersMillis ?: response?.responseHeadersMillis,
                )
            } else {
                event
            }
        last = safe
        onDiagnostic(safe)
    }
    try {
        record(AiProbeDiagnostic(AiProbeStage.Preparing))
        val provider =
            ConfiguredAiProvider(
                "canvas-understanding-probe",
                server,
                client,
                approve = { review ->
                    record(AiProbeDiagnostic(AiProbeStage.CheckingWorkspace))
                    review.request.prompt == prompt && approved()
                },
                credentialHeaders = { headers },
                onDiagnostic = ::record,
            )
        val request =
            AiCoworkRequest(
                randomUuid(),
                prompt,
                AiContextSnapshot("probe", 0, AiContextScope.PromptOnly, emptyList(), emptyList()),
            )
        val text = AiUtf8TextBuffer()
        var completed = false
        provider.stream(request).collect { event ->
            when (event) {
                is AiCoworkEvent.TextDelta -> {
                    val nextText = text.append(event.text)
                    if (last?.stage != AiProbeStage.Receiving) record(AiProbeDiagnostic(AiProbeStage.Receiving))
                    onText(nextText)
                }

                AiCoworkEvent.Completed -> {
                    completed = true
                }

                is AiCoworkEvent.Failed -> {
                    record(
                        AiProbeDiagnostic(
                            AiProbeStage.Failed,
                            when (event.message) {
                                "AI stream format or size is invalid" -> AiProbeFailure.StreamFormat
                                "AI stream ended before completion", "AI response did not finish normally" -> AiProbeFailure.Incomplete
                                "AI response requires an unsupported review mode" -> AiProbeFailure.UnsupportedMode
                                "AI provider returned an error" -> AiProbeFailure.ProviderError
                                else -> AiProbeFailure.Unknown
                            },
                        ),
                    )
                    error("AI probe failed")
                }

                is AiCoworkEvent.ProposedOperation -> {
                    record(AiProbeDiagnostic(AiProbeStage.Failed, AiProbeFailure.UnsupportedMode))
                    error("Probe does not accept operations")
                }
            }
        }
        if (!completed) {
            record(AiProbeDiagnostic(AiProbeStage.Failed, AiProbeFailure.Incomplete))
            error("Incomplete AI response")
        }
        val result = text.finish()
        if (result.isBlank()) {
            record(AiProbeDiagnostic(AiProbeStage.Failed, AiProbeFailure.EmptyResponse))
            error("Empty AI response")
        }
        val responseBytes = result.encodeToByteArray(throwOnInvalidSequence = true).size
        record(AiProbeDiagnostic(AiProbeStage.Completed, reply = AiReplyDiagnostic(responseBytes, 0, 0)))
        return result
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        onDiagnostic(AiProbeDiagnostic(AiProbeStage.Cancelled))
        throw cancelled
    } catch (failure: Exception) {
        record(
            AiProbeDiagnostic(
                AiProbeStage.Failed,
                when (failure) {
                    is AiRequestValidationException -> AiProbeFailure.Configuration
                    is AiTextLimitException -> AiProbeFailure.OutputLimit
                    is AiTextEncodingException -> AiProbeFailure.ResponseFormat
                    else -> AiProbeFailure.Unknown
                },
            ),
        )
        throw failure
    } finally {
        client.close()
    }
}
