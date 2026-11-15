package app.bbcrevived.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Frosted-glass surface: blurs whatever sits under it in [hz] (Haze; real blur on API 31+, scrim fallback otherwise).
 * The AGSL lens/refraction layer is planned on top of this for API 33+.
 */
@Composable
fun Modifier.glass(hz: HazeState, shape: Shape = CircleShape): Modifier {
    val surface = MaterialTheme.colorScheme.surface
    return this
        .clip(shape)
        .hazeEffect(
            state = hz,
            style = HazeStyle(backgroundColor = surface, tint = HazeTint(surface.copy(alpha = 0.40f)), blurRadius = 24.dp),
        )
        .border(1.dp, Color.White.copy(alpha = 0.28f), shape)
}

/** Glass pill with a highlight that slides between equal-width items with a springy overshoot. */
@Composable
fun SlidingPill(
    hz: HazeState,
    count: Int,
    selected: Int,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 56.dp,
    item: @Composable RowScope.(index: Int, selected: Boolean) -> Unit,
) {
    val frac by animateFloatAsState(selected.toFloat(), spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), label = "pill")
    val hi = MaterialTheme.colorScheme.primary
    val pad = with(androidx.compose.ui.platform.LocalDensity.current) { 5.dp.toPx() }
    Row(
        modifier.height(height).glass(hz).drawBehind {
            val w = size.width / count
            drawRoundRect(hi, Offset(frac * w + pad, pad), Size(w - 2 * pad, size.height - 2 * pad), CornerRadius(size.height))
        },
        verticalAlignment = Alignment.CenterVertically,
    ) { for (i in 0 until count) item(i, i == selected) }
}

fun Modifier.tap(onClick: () -> Unit) = clickable(onClick = onClick)
