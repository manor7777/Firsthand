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
            isRefreshing = refreshing, onRefresh = onRefresh, state = state, modifier = Modifier.fillMaxSize(),
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
fun MyNewsScreen(selected: List<String>, onChange: (List<String>) -> Unit) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).padding(top = top + 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("My News", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Pick categories. The order sets how For you is mixed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Categories.feeds.keys.forEach { c ->
                FilterChip(c in selected, { onChange(if (c in selected) selected - c else selected + c) }, { Text(c) }, Modifier.padding(bottom = 4.dp))
            }
        }
        SectionLabel("Your order")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 130.dp)) {
            itemsIndexed(selected) { i, c ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c, Modifier.weight(1f))
                        TextButton({ if (i > 0) onChange(selected.toMutableList().also { it.add(i - 1, it.removeAt(i)) }) }) { Text("↑") }
                        TextButton({ if (i < selected.lastIndex) onChange(selected.toMutableList().also { it.add(i + 1, it.removeAt(i)) }) }) { Text("↓") }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(query: String, pool: List<Story>, onOpen: (List<Story>, Int) -> Unit) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val q = query.trim().lowercase()
    val results = remember(q, pool) { if (q.isEmpty()) pool.take(20) else pool.filter { (it.title + " " + it.summary + " " + it.category).lowercase().contains(q) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp, top + 16.dp, 14.dp, 130.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(if (q.isEmpty()) "Recent stories" else "${results.size} results in your feeds", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        itemsIndexed(results) { i, s -> StoryCard(s, false) { onOpen(results, i) } }
    }
}
