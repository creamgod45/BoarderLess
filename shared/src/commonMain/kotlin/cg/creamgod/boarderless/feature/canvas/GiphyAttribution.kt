package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import boarderless.shared.generated.resources.Res
import boarderless.shared.generated.resources.giphy_powered_by
import org.jetbrains.compose.resources.painterResource

/** Original approved static mark; no tint, crop, redraw, animation or GIPHY API/CDN request. */
@Composable internal fun GiphyAttribution() {
    Image(
        painterResource(Res.drawable.giphy_powered_by),
        contentDescription = "Powered By GIPHY",
        contentScale = ContentScale.Fit,
        modifier = Modifier.width(200.dp).height(42.dp),
    )
}
