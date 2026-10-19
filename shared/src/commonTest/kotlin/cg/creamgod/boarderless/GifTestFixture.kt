package cg.creamgod.boarderless

import kotlin.io.encoding.Base64

/** GIF89a: 1x1 red/blue, delays 50/100ms, NETSCAPE infinite loop; independent full frames. */
internal fun twoFrameGifFixture() =
    Base64.decode(
        "R0lGODlhAQABAIAAAP8AAAAA/yH/C05FVFNDQVBFMi4wAwEAAAAh+QQEBQAAACwAAAAAAQABAAACAkQBACH5BAQKAAAALAAAAAABAAEAAAICTAEAOw==",
    )
