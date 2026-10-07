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
        val text = StringBuilder()
        var completed = false
        provider.stream(request).collect { event ->
            when (event) {
                is AiCoworkEvent.TextDelta -> {
                    // Enforce the advertised UTF-8 byte limit, not Kotlin UTF-16 character count.
                    val nextText = text.toString() + event.text
                    if (nextText.encodeToByteArray().size > 128 * 1024) {
                        record(AiProbeDiagnostic(AiProbeStage.Failed, AiProbeFailure.OutputLimit))
                        error("AI output limit")
                    }
                    if (last?.stage != AiProbeStage.Receiving) record(AiProbeDiagnostic(AiProbeStage.Receiving))
                    text.append(event.text)
                    onText(text.toString())
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
        if (!completed ||
            text.isBlank()
        ) {
            record(AiProbeDiagnostic(AiProbeStage.Failed, AiProbeFailure.Incomplete))
            error("Incomplete AI response")
        }
        record(AiProbeDiagnostic(AiProbeStage.Completed))
        return text.toString()
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        onDiagnostic(AiProbeDiagnostic(AiProbeStage.Cancelled))
        throw cancelled
    } catch (failure: Exception) {
        record(
            AiProbeDiagnostic(
                AiProbeStage.Failed,
                if (failure is AiRequestValidationException) AiProbeFailure.Configuration else AiProbeFailure.Unknown,
            ),
        )
        throw failure
    } finally {
        client.close()
    }
}
