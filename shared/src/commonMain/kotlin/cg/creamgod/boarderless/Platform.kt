package cg.creamgod.boarderless

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform