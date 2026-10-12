package cg.creamgod.boarderless.domain.media

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/** Durable provider identity only. Runtime URLs, keys, paths and downloaded bytes never belong here. */
@Serializable
enum class ExternalMediaProvider { @SerialName("giphy") Giphy }

@Serializable
data class ExternalMediaReference(
    val provider: ExternalMediaProvider,
    val id: String,
) {
    init {
        require(isExternalMediaId(id)) { "Invalid external media identity" }
    }
}

/** IDs are opaque, bounded path-free tokens, not media URLs or local asset identities. */
fun isExternalMediaId(id: String): Boolean = id.length in 1..128 && id.all {
    it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' || it == '-'
}
