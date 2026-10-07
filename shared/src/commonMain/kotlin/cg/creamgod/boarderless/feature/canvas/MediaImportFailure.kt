package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.data.AssetImportException
import cg.creamgod.boarderless.data.AssetImportIssue
import cg.creamgod.boarderless.data.AssetImportStage
import cg.creamgod.boarderless.i18n.Strings

/** Describes where import stopped, not a guessed network/codec root cause. No raw error text. */
internal enum class MediaImportFailure {
    UnsupportedFormat,
    TooLarge,
    InvalidFile,
    Selecting,
    Preparing,
    Uploading,
    Confirming,
    Processing,
    Unknown,
    ;

    fun message(): String =
        when (this) {
            UnsupportedFormat -> Strings.media.unsupportedAsset()
            TooLarge -> Strings.media.assetTooLarge()
            InvalidFile -> Strings.media.importInvalidFile()
            Selecting -> Strings.media.importSelectionFailed()
            Preparing -> Strings.media.importPreparationFailed()
            Uploading -> Strings.media.importUploadFailed()
            Confirming -> Strings.media.importConfirmationFailed()
            Processing -> Strings.media.importProcessingFailed()
            Unknown -> Strings.media.importFailed()
        }
}

internal fun mediaImportFailure(
    error: Throwable,
    stage: AssetImportStage?,
): MediaImportFailure {
    if (error is AssetImportException) {
        when (error.issue) {
            AssetImportIssue.UnsupportedMediaType -> {
                return MediaImportFailure.UnsupportedFormat
            }

            AssetImportIssue.AssetTooLarge -> {
                return MediaImportFailure.TooLarge
            }

            AssetImportIssue.BlankName, AssetImportIssue.InvalidByteSize, AssetImportIssue.InvalidChecksum,
            AssetImportIssue.InvalidDimensions, AssetImportIssue.InvalidDuration, AssetImportIssue.TruncatedSource,
            -> {
                return MediaImportFailure.InvalidFile
            }

            AssetImportIssue.Rejected, AssetImportIssue.Missing, AssetImportIssue.NotReady -> {
                return MediaImportFailure.Processing
            }
        }
    }
    return when (stage) {
        null -> MediaImportFailure.Selecting
        AssetImportStage.Validating -> MediaImportFailure.InvalidFile
        AssetImportStage.Preparing -> MediaImportFailure.Preparing
        is AssetImportStage.Uploading -> MediaImportFailure.Uploading
        AssetImportStage.Confirming -> MediaImportFailure.Confirming
        is AssetImportStage.Processing -> MediaImportFailure.Processing
        is AssetImportStage.Ready, is AssetImportStage.RecoveryRequired -> MediaImportFailure.Unknown
    }
}
