package cg.creamgod.boarderless.data.remote

import platform.Foundation.NSBundle

private const val SimulatorBackendBaseUrl = "http://127.0.0.1:3000"

internal actual fun defaultBackendBaseUrl(): String = configuredBackendBaseUrl(
    configured = NSBundle.mainBundle.objectForInfoDictionaryKey("BoarderLessBackendURL") as? String,
    fallback = SimulatorBackendBaseUrl,
)
