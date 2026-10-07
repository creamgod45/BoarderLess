package cg.creamgod.boarderless.data.remote

import platform.Foundation.NSBundle

private const val SimulatorBackendBaseUrl = "http://192.168.68.65:3000"

internal actual fun defaultBackendBaseUrl(): String =
    configuredBackendBaseUrl(
        configured = NSBundle.mainBundle.objectForInfoDictionaryKey("BoarderLessBackendURL") as? String,
        fallback = SimulatorBackendBaseUrl,
    )
