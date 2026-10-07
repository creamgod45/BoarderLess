package cg.creamgod.boarderless

import boarderless.shared.generated.resources.Res

internal actual suspend fun readGiphyBrandResourceForTest(): ByteArray =
    Res.readBytes("drawable/giphy_powered_by.png")
