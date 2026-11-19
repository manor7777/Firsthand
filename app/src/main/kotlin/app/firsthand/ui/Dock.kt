package app.firsthand.ui

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

/** The three bottom tabs. For you and Breaking are the two Home pages; which comes first (and opens first) is a Settings choice. */
enum class Tab(val label: String, val icon: ImageVector, val iconOff: ImageVector) {
    ForYou("For you", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    Breaking("Breaking", Icons.Filled.Bolt, Icons.Outlined.Bolt),
    MyNews("My News", Icons.Filled.Newspaper, Icons.Outlined.Newspaper),
}

/** The tabs in display order: Breaking only exists while BBC News is a source. */
fun tabOrder(breakingFirst: Boolean, bbcOn: Boolean): List<Tab> = when {
    !bbcOn -> listOf(Tab.ForYou, Tab.MyNews)
    breakingFirst -> listOf(Tab.Breaking, Tab.ForYou, Tab.MyNews)
    else -> listOf(Tab.ForYou, Tab.Breaking, Tab.MyNews)
}

/**
 * One wide glass pill of tabs plus a Search button. Searching collapses the pill into one
 * circle (showing the tab you came from) and shows the search field. Tapping the circle or X exits search.
 */
@Composable
fun GlassDock(
    hz: HazeState,
    tabs: List<Tab>,
    tab: Tab,
    searching: Boolean,
    query: String,
    onTab: (Tab) -> Unit,
    onSearch: () -> Unit,
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
            // The tabs, then Search as a fourth button (it never stays selected: it turns the bar into the search field).
            SlidingPill(hz, tabs.size + 1, tabs.indexOf(tab).coerceAtLeast(0), Modifier.weight(1f), height = 64.dp) { i, sel ->
                if (i == tabs.size) {
                    // After the tabs: Search. It never stays selected; it turns the bar into the search field.
                    val c = MaterialTheme.colorScheme.onSurfaceVariant
                    Column(Modifier.weight(1f).tap(onSearch).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Search, "Search", tint = c)
                        Text("Search", color = c, style = MaterialTheme.typography.labelMedium)
                    }
                    return@SlidingPill
                }
                val t = tabs[i]
                val c = if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                Column(Modifier.weight(1f).tap { onTab(t) }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
