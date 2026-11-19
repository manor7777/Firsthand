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
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f), shape)
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
    float sheen = (1.0 - p.y / size.y) * 0.05 * (1.0 - rim);
    float a = rim * 0.12 + sheen;
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

/**
 * Glass pill with a droplet highlight. Moving to another item stretches it like a bubble pulling apart: the edge on the
 * side it is heading to runs ahead, the other edge lags and then catches up, with a thinning "neck" in between while
 * stretched, and it settles with a little bounce. While an item is being dragged, [position] places it directly instead.
 */
@Composable
fun SlidingPill(
    hz: HazeState,
    count: Int,
    selected: Int,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 56.dp,
    /** Stable identity per item, so an item keeps its state (and an ongoing drag) when the items are reordered. */
    keys: List<Any>? = null,
    /** Where the highlight is, in item slots (fractions allowed), overriding the animation: used while an item is being dragged. */
    position: Float? = null,
    item: @Composable RowScope.(index: Int, selected: Boolean) -> Unit,
) {
    val last = remember { intArrayOf(selected) }
    val goingRight = remember(selected) { (selected >= last[0]).also { last[0] = selected } }
    val lead = spring<Float>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium)
    val trail = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
    val leftSlot by animateFloatAsState(selected.toFloat(), if (goingRight) trail else lead, label = "pillLeft")
    val rightSlot by animateFloatAsState(selected + 1f, if (goingRight) lead else trail, label = "pillRight")
    // A quiet frosted highlight, not a solid accent block: lighter glass inside the bar.
    val onSurface = MaterialTheme.colorScheme.onSurface
    val pad = with(androidx.compose.ui.platform.LocalDensity.current) { 5.dp.toPx() }
    Row(
        modifier.defaultMinSize(minHeight = height).height(androidx.compose.foundation.layout.IntrinsicSize.Min).glass(hz).drawBehind {
            val w = size.width / count
            val l = position ?: leftSlot
            val r = if (position != null) position + 1f else rightSlot
            val stretch = (r - l - 1f).coerceIn(0f, 1.6f) // how far the droplet is pulled out beyond one slot
            val x0 = l * w + pad
            val x1 = r * w - pad
            val h = size.height - 2 * pad
            val corner = CornerRadius(size.height)
            val shape = androidx.compose.ui.graphics.Path()
            val cap = w - 2 * pad
            if (stretch < 0.05f || x1 - x0 <= cap * 1.05f) {
                shape.addRoundRect(androidx.compose.ui.geometry.RoundRect(x0, pad, x1, pad + h, corner))
            } else {
                // Two rounded ends joined by a thinner waist: the bubble separating.
                val waist = h * (1f - 0.3f * (stretch / 1.6f).coerceIn(0f, 1f)) / 2f
                val mid = pad + h / 2f
                val a = androidx.compose.ui.graphics.Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(x0, pad, x0 + cap, pad + h, corner)) }
                val b = androidx.compose.ui.graphics.Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(x1 - cap, pad, x1, pad + h, corner)) }
                val bridge = androidx.compose.ui.graphics.Path().apply { addRect(androidx.compose.ui.geometry.Rect(x0 + cap / 2f, mid - waist, x1 - cap / 2f, mid + waist)) }
                val ab = androidx.compose.ui.graphics.Path.combine(androidx.compose.ui.graphics.PathOperation.Union, a, b)
                shape.addPath(androidx.compose.ui.graphics.Path.combine(androidx.compose.ui.graphics.PathOperation.Union, ab, bridge))
            }
            // Brighter while it is stretched, like light gathering in the bubble.
            drawPath(shape, onSurface.copy(alpha = 0.13f + 0.07f * (stretch / 1.6f)))
            drawPath(shape, onSurface.copy(alpha = 0.12f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        },
        verticalAlignment = Alignment.CenterVertically,
    ) { for (i in 0 until count) androidx.compose.runtime.key(keys?.getOrNull(i) ?: i) { item(i, i == selected) } }
}

fun Modifier.tap(onClick: () -> Unit) = clickable(onClick = onClick)

/** The title row every top-level page shares (same type size, height and icon position), so the pages line up when you switch between them. */
@Composable
fun PageHeaderRow(
    title: String, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        leading?.invoke()
        Text(
            title, Modifier.weight(1f).padding(start = if (leading == null) 8.dp else 0.dp),
            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

/** A filter chip in the same glass style as the pills and dock: frosted when off, the accent colour when on. */
@Composable
fun GlassChip(hz: HazeState, selected: Boolean, label: String, onClick: () -> Unit) {
    val shape = CircleShape
    Box(
        Modifier.heightIn(min = 40.dp).let { if (selected) it.clip(shape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f), shape) else it.glass(hz, shape) }
            .tap(onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1, style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
