package app.firsthand.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Search
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

enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    MyNews("My News", Icons.Filled.Newspaper),
}

/**
 * Two glass tabs + a detached round search pill. Tapping search collapses the tabs into one circle
 * (showing the tab you came from) and expands the pill into a field. Tapping the circle or X exits search.
 */
@Composable
fun GlassDock(
    hz: HazeState,
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
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).navigationBarsPadding().animateContentSize(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!searching) {
            SlidingPill(hz, Tab.entries.size, tab.ordinal, Modifier.weight(1f), height = 64.dp) { i, sel ->
                val t = Tab.entries[i]
                val c = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                Column(Modifier.weight(1f).tap { onTab(t) }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(t.icon, t.label, tint = c)
                    Text(t.label, color = c, style = MaterialTheme.typography.labelMedium)
                }
            }
            Box(Modifier.size(64.dp).glass(hz).tap(onSearch), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Search, "Search", tint = MaterialTheme.colorScheme.onSurface)
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
