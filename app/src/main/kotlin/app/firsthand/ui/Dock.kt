package app.firsthand.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Search as SearchOutlined
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.zIndex
import app.firsthand.data.BarOrder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

/** The bottom bar's buttons. Search is one too: it opens the search field instead of a page. The user orders them (see [BarOrder]). */
enum class Tab(val id: String, val label: String, val icon: ImageVector, val iconOff: ImageVector) {
    ForYou(BarOrder.FOR_YOU, "For you", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    Breaking(BarOrder.BREAKING, "Breaking", Icons.Filled.Bolt, Icons.Outlined.Bolt),
    MyNews(BarOrder.MY_NEWS, "My News", Icons.Filled.Newspaper, Icons.Outlined.Newspaper),
    Search(BarOrder.SEARCH, "Search", Icons.Filled.Search, Icons.Outlined.SearchOutlined);

    companion object {
        fun of(id: String): Tab? = entries.firstOrNull { it.id == id }
    }
}

/**
 * One wide glass pill of buttons in the user's order. Press and hold a button, then drag it sideways to rearrange; the new
 * order is saved when you let go. Search is a button like the rest (it never stays selected: it turns the bar into the
 * search field, and tapping the circle or X exits search).
 */
@Composable
fun GlassDock(
    hz: HazeState,
    items: List<Tab>,
    tab: Tab,
    searching: Boolean,
    query: String,
    onTab: (Tab) -> Unit,
    onSearch: () -> Unit,
    onReorder: (List<Tab>) -> Unit,
    onExit: () -> Unit,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).windowInsetsPadding(if (searching) WindowInsets.ime.union(WindowInsets.navigationBars) else WindowInsets.navigationBars).animateContentSize(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!searching) {
            val view = LocalView.current
            // The order shown while dragging; saved (onReorder) when the finger lifts.
            var work by remember(items) { mutableStateOf(items) }
            var dragging by remember { mutableStateOf<Tab?>(null) }
            var dragX by remember { mutableFloatStateOf(0f) }
            var barW by remember { mutableIntStateOf(0) }
            var quietUntil by remember { mutableLongStateOf(0L) } // a press-and-hold that ends without a drag must not also count as a tap
            val itemW = if (work.isEmpty()) 1f else barW.toFloat() / work.size
            fun finish() { dragging = null; dragX = 0f; quietUntil = android.os.SystemClock.uptimeMillis() + 350; if (work != items) onReorder(work) }
            SlidingPill(
                hz, work.size, work.indexOf(tab).coerceAtLeast(0), Modifier.weight(1f).onSizeChanged { barW = it.width },
                height = 64.dp, keys = work.map { it.id },
            ) { i, sel ->
                val t = work[i]
                val lifted = dragging == t
                val c = if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    Modifier.weight(1f)
                        .zIndex(if (lifted) 1f else 0f)
                        .graphicsLayer { if (lifted) { translationX = dragX; scaleX = 1.12f; scaleY = 1.12f; alpha = 0.92f } }
                        .pointerInput(t) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragging = t; dragX = 0f; view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS) },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragX += amount.x
                                    val at = work.indexOf(t)
                                    if (dragX > itemW / 2 && at < work.lastIndex) {
                                        work = work.toMutableList().also { l -> l.add(at + 1, l.removeAt(at)) }; dragX -= itemW
                                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                    } else if (dragX < -itemW / 2 && at > 0) {
                                        work = work.toMutableList().also { l -> l.add(at - 1, l.removeAt(at)) }; dragX += itemW
                                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                },
                                onDragEnd = { finish() },
                                onDragCancel = { finish() },
                            )
                        }
                        .tap { if (dragging == null && android.os.SystemClock.uptimeMillis() >= quietUntil) { if (t == Tab.Search) onSearch() else onTab(t) } }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(if (sel) t.icon else t.iconOff, t.label, tint = c)
                    Text(t.label, color = c, style = MaterialTheme.typography.labelMedium, fontWeight = if (sel) androidx.compose.ui.text.font.FontWeight.SemiBold else null)
                }
            }
        } else {
            val focus = FocusRequester()
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            // Always-visible way out: the circle returns to the tab you came from.
            Box(Modifier.size(64.dp).glass(hz).tap(onExit), contentAlignment = Alignment.Center) {
                Icon(tab.icon, "Back to ${tab.label}", tint = MaterialTheme.colorScheme.onSurface)
            }
            Row(Modifier.weight(1f).height(64.dp).glass(hz, CircleShape).padding(start = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    if (query.isEmpty()) Text("Search news", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BasicTextField(
                        query, onQuery, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(),
                    )
                }
                Box(Modifier.size(56.dp).tap { if (query.isEmpty()) onExit() else onQuery("") }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, "Clear")
                }
            }
        }
    }
}

/** One-time tip over the page (above the bar): the bar can be rearranged. Tap anywhere to dismiss. */
@Composable
fun BarTip(onDismiss: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f)).tap(onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        androidx.compose.material3.Surface(
            Modifier.padding(horizontal = 24.dp).padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 120.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Make the bar yours", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text("Press and hold any button down here, then drag it left or right to put the buttons in the order you like. The first one is your home page.", style = MaterialTheme.typography.bodyMedium)
                Text("Tap anywhere to dismiss", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
