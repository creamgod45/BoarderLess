package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlin.test.*

class MediaImportFailureTest {
    private val error = IllegalStateException("secret signed URL / provider / path")

    @Test fun classifiesImportBoundariesWithoutGuessingRootCause() {
        assertEquals(MediaImportFailure.Selecting, mediaImportFailure(error, null))
        assertEquals(MediaImportFailure.InvalidFile, mediaImportFailure(error, AssetImportStage.Validating))
        assertEquals(MediaImportFailure.Preparing, mediaImportFailure(error, AssetImportStage.Preparing))
        assertEquals(MediaImportFailure.Uploading, mediaImportFailure(error, AssetImportStage.Uploading(0, 12)))
        assertEquals(MediaImportFailure.Confirming, mediaImportFailure(error, AssetImportStage.Confirming))
        assertEquals(MediaImportFailure.Processing, mediaImportFailure(error, AssetImportStage.Processing(AssetStatus.Pending)))
        assertEquals(MediaImportFailure.Unknown, mediaImportFailure(error, AssetImportStage.RecoveryRequired("asset")))
    }

    @Test fun typedFileFailuresOverrideStageAndDoNotDependOnErrorText() {
        AssetImportIssue.entries.forEach { issue ->
            val expected = when (issue) {
                AssetImportIssue.UnsupportedMediaType -> MediaImportFailure.UnsupportedFormat
                AssetImportIssue.AssetTooLarge -> MediaImportFailure.TooLarge
                AssetImportIssue.Rejected, AssetImportIssue.Missing, AssetImportIssue.NotReady -> MediaImportFailure.Processing
                else -> MediaImportFailure.InvalidFile
            }
            assertEquals(expected, mediaImportFailure(AssetImportException(issue, "secret"), null))
            assertEquals(expected, mediaImportFailure(AssetImportException(issue, "different"), AssetImportStage.Preparing))
        }
    }

    @Test fun userMessagesNeverIncludeRawFailureText() {
        MediaImportFailure.entries.forEach { category ->
            val text = category.message()
            assertTrue(text.isNotBlank())
            assertFalse(text.contains("secret"))
            assertFalse(text.contains("http://"))
            assertFalse(text.contains("https://"))
        }
    }
}
