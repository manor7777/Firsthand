package app.firsthand.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ShaderBrush
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

/**
 * Frosted-glass surface: blurs whatever sits under it in [hz] (Haze; real blur on API 31+, scrim fallback otherwise).
 * The AGSL lens/refraction layer is planned on top of this for API 33+.
 */
@Composable
fun Modifier.glass(hz: HazeState, shape: Shape = CircleShape): Modifier {
    val surface = MaterialTheme.colorScheme.surface
    return this
        .hazeChild(
            state = hz,
            shape = shape,
            style = HazeStyle(backgroundColor = surface, tint = HazeTint(surface.copy(alpha = 0.40f)), blurRadius = 24.dp),
        )
        .clip(shape)
        .border(1.dp, Color.White.copy(alpha = 0.28f), shape)
        .then(if (Build.VERSION.SDK_INT >= 33) Modifier.lensHighlight() else Modifier)
}

/**
 * A band of heavy frosted blur along the top or bottom edge, faded out towards the content, so stories scrolling under the
 * header buttons or the dock dissolve into a soft blur instead of showing sharp text beside them. The caller sets the height.
 */
@Composable
fun EdgeScrim(hz: HazeState, atTop: Boolean, modifier: Modifier = Modifier) {
    val surface = MaterialTheme.colorScheme.surface
    // Opaque mask where the glass should show, transparent where it fades into the page.
    val mask = if (atTop) Brush.verticalGradient(0f to Color.Black, 0.8f to Color.Black, 1f to Color.Transparent)
    else Brush.verticalGradient(0f to Color.Transparent, 0.2f to Color.Black, 1f to Color.Black)
    Box(
        modifier.fillMaxWidth()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent { drawContent(); drawRect(mask, blendMode = BlendMode.DstIn) }
            .hazeChild(
                state = hz, shape = RectangleShape,
                style = HazeStyle(backgroundColor = surface, tint = HazeTint(surface.copy(alpha = 0.55f)), blurRadius = 32.dp),
            ),
    )
}

// AGSL (API 33+): rim light that brightens toward the edge plus a soft top sheen, like light bending through glass.
private const val LENS_SRC = """
uniform float2 size;
half4 main(float2 p) {
    float r = min(size.x, size.y) * 0.5;
    float2 q = abs(p - size * 0.5) - (size * 0.5 - float2(r));
    float d = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
    float rim = smoothstep(-7.0, 0.0, d);
    float sheen = (1.0 - p.y / size.y) * 0.10 * (1.0 - rim);
    float a = rim * 0.34 + sheen;
    return half4(a, a, a, a);
}
"""

@androidx.annotation.RequiresApi(33)
private fun Modifier.lensHighlight(): Modifier = drawWithCache {
    // If the shader fails to compile on some GPU/driver, skip the highlight instead of crashing.
    val brush = runCatching {
        val shader = RuntimeShader(LENS_SRC)
        shader.setFloatUniform("size", size.width, size.height)
        ShaderBrush(shader)
    }.getOrNull()
    onDrawWithContent { drawContent(); brush?.let { drawRect(it) } }
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
