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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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

private val bubbleLead = spring<Float>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium)
private val bubbleTrail = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)

/**
 * Draws the droplet highlight between [x0] and [x1] (pixels). [rest] is its width when settled on one item: stretched beyond that
 * it becomes two rounded ends joined by a thinner waist, like a bubble pulling apart, and glows a little brighter.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBubble(x0: Float, x1: Float, top: Float, h: Float, rest: Float, color: Color) {
    val stretch = ((x1 - x0 - rest) / rest.coerceAtLeast(1f)).coerceIn(0f, 1.6f)
    val corner = CornerRadius(h)
    val shape = androidx.compose.ui.graphics.Path()
    if (stretch < 0.05f) {
        shape.addRoundRect(androidx.compose.ui.geometry.RoundRect(x0, top, x1, top + h, corner))
    } else {
        val cap = rest
        val waist = h * (1f - 0.3f * (stretch / 1.6f).coerceIn(0f, 1f)) / 2f
        val mid = top + h / 2f
        val a = androidx.compose.ui.graphics.Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(x0, top, x0 + cap, top + h, corner)) }
        val b = androidx.compose.ui.graphics.Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(x1 - cap, top, x1, top + h, corner)) }
        val bridge = androidx.compose.ui.graphics.Path().apply { addRect(androidx.compose.ui.geometry.Rect(x0 + cap / 2f, mid - waist, x1 - cap / 2f, mid + waist)) }
        val ab = androidx.compose.ui.graphics.Path.combine(androidx.compose.ui.graphics.PathOperation.Union, a, b)
        shape.addPath(androidx.compose.ui.graphics.Path.combine(androidx.compose.ui.graphics.PathOperation.Union, ab, bridge))
    }
    drawPath(shape, color.copy(alpha = 0.13f + 0.07f * (stretch / 1.6f)))
    drawPath(shape, color.copy(alpha = 0.12f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
}

/**
 * Glass pill with a droplet highlight. Moving to another item stretches it like a bubble pulling apart: the edge on the
 * side it is heading to runs ahead, the other edge lags and then catches up, and it settles with a little bounce.
 * While an item is being dragged, [position] places it directly instead.
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
    val leftSlot by animateFloatAsState(selected.toFloat(), if (goingRight) bubbleTrail else bubbleLead, label = "pillLeft")
    val rightSlot by animateFloatAsState(selected + 1f, if (goingRight) bubbleLead else bubbleTrail, label = "pillRight")
    val onSurface = MaterialTheme.colorScheme.onSurface
    val pad = with(androidx.compose.ui.platform.LocalDensity.current) { 5.dp.toPx() }
    Row(
        modifier.defaultMinSize(minHeight = height).height(androidx.compose.foundation.layout.IntrinsicSize.Min).glass(hz).drawBehind {
            val w = size.width / count
            val l = position ?: leftSlot
            val r = if (position != null) position + 1f else rightSlot
            drawBubble(l * w + pad, r * w - pad, pad, size.height - 2 * pad, w - 2 * pad, onSurface)
        },
        verticalAlignment = Alignment.CenterVertically,
    ) { for (i in 0 until count) androidx.compose.runtime.key(keys?.getOrNull(i) ?: i) { item(i, i == selected) } }
}

/**
 * A scrolling row of glass chips with the same droplet highlight as [SlidingPill]: it slides to the chip you tap, stretching
 * like a bubble. The highlight is drawn over the chips as a light tint, so it shows across the ones it passes.
 */
@Composable
fun ChipRow(hz: HazeState, labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scroll = androidx.compose.foundation.rememberScrollState()
    val spots = remember { androidx.compose.runtime.mutableStateMapOf<Int, Pair<Float, Float>>() } // chip index -> left, width (px)
    val sel = selected.coerceIn(0, (labels.size - 1).coerceAtLeast(0))
    val last = remember { intArrayOf(sel) }
    val goingRight = remember(sel) { (sel >= last[0]).also { last[0] = sel } }
    val spot = spots[sel]
    val left by animateFloatAsState(spot?.first ?: 0f, if (goingRight) bubbleTrail else bubbleLead, label = "chipLeft")
    val right by animateFloatAsState(spot?.let { it.first + it.second } ?: 0f, if (goingRight) bubbleLead else bubbleTrail, label = "chipRight")
    val onSurface = MaterialTheme.colorScheme.onSurface
    androidx.compose.foundation.layout.Row(
        modifier.padding(vertical = 4.dp).horizontalScroll(scroll).drawWithContent {
            drawContent()
            if (spot != null) drawBubble(left, right, 0f, size.height, spot.second, onSurface)
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(8.dp)) // with the 8dp spacing this is the 16dp screen margin, inside the scrolling area
        labels.forEachIndexed { i, label ->
            GlassChip(hz, i == sel, label, Modifier.onPlaced { spots[i] = it.positionInParent().x to it.size.width.toFloat() }) { onSelect(i) }
        }
        Spacer(Modifier.width(8.dp))
    }
}

/** A tap with no press ripple: on the glass pills and chips the square ripple showed up before the press had finished. */
fun Modifier.tap(onClick: () -> Unit): Modifier = this.composed {
    clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onClick)
}

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

/** A filter chip in the same glass style as the pills and dock. The "selected" look is the droplet [ChipRow] draws over it. */
@Composable
fun GlassChip(hz: HazeState, selected: Boolean, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 40.dp).glass(hz, CircleShape).tap(onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1, style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
