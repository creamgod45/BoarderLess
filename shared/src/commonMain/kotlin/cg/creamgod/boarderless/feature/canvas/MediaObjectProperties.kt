package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.CanvasObject
import cg.creamgod.boarderless.domain.model.ExternalMediaNode
import cg.creamgod.boarderless.domain.model.MediaNode

/** Shared inspector capabilities; external references never acquire a workspace asset ID. */
internal val CanvasObject.isMediaObject: Boolean
    get() = this is MediaNode || this is ExternalMediaNode

internal val CanvasObject.mediaAltText: String
    get() = when (this) {
        is MediaNode -> altText
        is ExternalMediaNode -> altText
        else -> error("Object has no media alternative text")
    }
