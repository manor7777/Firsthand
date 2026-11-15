package app.bbcrevived.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.bbcrevived.data.Categories
import app.bbcrevived.data.Story
import coil.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

private fun ago(ms: Long): String {
    if (ms <= 0) return ""
    val m = ((System.currentTimeMillis() - ms) / 60000).coerceAtLeast(0)
    return if (m < 60) "$m min ago" else if (m < 1440) "${m / 60} h ago" else "${m / 1440} d ago"
}

@Composable
fun StoryCard(s: Story, big: Boolean, rank: Int? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Card(onClick = onClick, shape = shape, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        if (big) {
            Column {
                s.imageUrl?.let { AsyncImage(it, null, Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Crop) }
                Column(Modifier.padding(14.dp)) { Titles(s, MaterialTheme.typography.titleLarge) }
            }
        } else {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                rank?.let { Text("$it", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(30.dp)) }
                Column(Modifier.weight(1f)) { Titles(s, MaterialTheme.typography.titleMedium) }
                s.imageUrl?.let { AsyncImage(it, null, Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop) }
            }
        }
    }
}

@Composable
private fun Titles(s: Story, style: androidx.compose.ui.text.TextStyle) {
    Text(s.title, style = style, fontWeight = FontWeight.SemiBold, maxLines = 4, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(4.dp))
    Text(
        (if (s.isLive) "LIVE · " else "") + listOf(s.category, ago(s.publishedMillis)).filter { it.isNotEmpty() }.joinToString(" · "),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SectionLabel(t: String) =
    Text(t.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    hz: HazeState, breaking: List<Story>, mostRead: List<Story>, forYou: List<Story>,
    refreshing: Boolean, onRefresh: () -> Unit, onOpen: (List<Story>, Int) -> Unit,
) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val view = LocalView.current
    val state = rememberPullToRefreshState()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    LaunchedEffect(state.distanceFraction >= 1f) { if (state.distanceFraction >= 1f) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM) }

    val flat = if (mode == 0) breaking.take(5) + mostRead.take(5) else forYou

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = refreshing, onRefresh = onRefresh, state = state, modifier = Modifier.fillMaxSize().haze(hz),
            indicator = {
                PullToRefreshDefaults.Indicator(state, refreshing, Modifier.align(Alignment.TopCenter).padding(top = top + 110.dp))
            },
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = top + 120.dp, bottom = 130.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (mode == 0) {
                    item { SectionLabel("Breaking now") }
                    itemsIndexed(breaking.take(5)) { i, s -> StoryCard(s, big = i == 0) { onOpen(flat, i) } }
                    item { SectionLabel("Most read") }
                    itemsIndexed(mostRead.take(5)) { i, s -> StoryCard(s, false, rank = i + 1) { onOpen(flat, breaking.take(5).size + i) } }
                } else {
                    item { SectionLabel("Top for you") }
                    itemsIndexed(forYou) { i, s -> StoryCard(s, big = i == 0) { onOpen(flat, i) } }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = top + 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("BBC Revived", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            SlidingPill(hz, 2, mode, Modifier.fillMaxWidth(), height = 46.dp) { i, sel ->
                Box(Modifier.weight(1f).fillMaxHeight().tap { mode = i; view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }, contentAlignment = Alignment.Center) {
                    Text(if (i == 0) "Breaking" else "For you", fontWeight = FontWeight.SemiBold,
                        color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyNewsScreen(hz: HazeState, selected: List<String>, onChange: (List<String>) -> Unit) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var q by rememberSaveable { mutableStateOf("") }
    val query = q.trim()
    val matches = remember(query) { Categories.catalog.filter { query.isEmpty() || it.name.contains(query, true) || it.group.contains(query, true) } }
    val kw = Categories.KW + query.lowercase()
    fun toggle(f: String) = onChange(if (f in selected) selected - f else selected + f)
    fun move(i: Int, d: Int) = onChange(selected.toMutableList().also { it.add(i + d, it.removeAt(i)) })

    LazyColumn(
        Modifier.fillMaxSize().haze(hz),
        contentPadding = PaddingValues(18.dp, top + 16.dp, 18.dp, 130.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("My News", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Follow topics or any keyword. Order sets how For you is mixed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search topics or follow a keyword") }, shape = RoundedCornerShape(28.dp))
        }
        if (query.isNotEmpty()) {
            item {
                FilledTonalButton({ toggle(kw); q = "" }, Modifier.fillMaxWidth()) {
                    Text(if (kw in selected) "Unfollow “$query”" else "Follow keyword “$query”")
                }
            }
            if (matches.isEmpty()) item { Text("No matching topics.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (selected.isNotEmpty() && query.isEmpty()) {
            item { SectionLabel("Following · ${selected.size}") }
            itemsIndexed(selected) { i, c ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(Categories.label(c), Modifier.weight(1f))
                        TextButton({ if (i > 0) move(i, -1) }) { Text("↑") }
                        TextButton({ if (i < selected.lastIndex) move(i, 1) }) { Text("↓") }
                        TextButton({ toggle(c) }) { Text("Remove") }
                    }
                }
            }
        }
        matches.groupBy { it.group }.forEach { (group, topics) ->
            item { SectionLabel(group) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    topics.forEach { t -> FilterChip(t.name in selected, { toggle(t.name) }, { Text(t.name) }, Modifier.padding(bottom = 4.dp)) }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(hz: HazeState, query: String, pool: List<Story>, onOpen: (List<Story>, Int) -> Unit) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val q = query.trim().lowercase()
    val results = remember(q, pool) { if (q.isEmpty()) pool.take(20) else pool.filter { (it.title + " " + it.summary + " " + it.category).lowercase().contains(q) } }
    LazyColumn(Modifier.fillMaxSize().haze(hz), contentPadding = PaddingValues(14.dp, top + 16.dp, 14.dp, 130.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(if (q.isEmpty()) "Recent stories" else "${results.size} results in your feeds", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        itemsIndexed(results) { i, s -> StoryCard(s, false) { onOpen(results, i) } }
    }
}
