package app.firsthand.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
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
import app.firsthand.data.Categories
import app.firsthand.data.NewsGroups
import app.firsthand.data.Source
import app.firsthand.data.Story
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

/** True while more than one source is in use (BBC News counts): only then do cards say which source a story is from. */
val LocalShowSource = compositionLocalOf { false }

@Composable
private fun Titles(s: Story, style: androidx.compose.ui.text.TextStyle) {
    Text(s.title, style = style, fontWeight = FontWeight.SemiBold, maxLines = 4, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(6.dp))
    // Pills say what and who: BREAKING / LIVE when flagged, then the source on every story ("BBC News", "Mirror"),
    // then the section and age as plain text, which gives way (ellipsis) when the row is full.
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (s.breaking) Pill("BREAKING", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        if (s.isLive) Pill("LIVE", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        if (LocalShowSource.current) Pill(s.sourceName, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        val rest = listOf(s.sectionName, ago(s.publishedMillis)).filter { it.isNotEmpty() }.joinToString(" · ")
        if (rest.isNotEmpty()) Text(
            rest, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Pill(text: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(50), color = container, contentColor = content) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun SectionLabel(t: String) =
    Text(t.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    hz: HazeState, breaking: List<Story>, mostRead: List<Story>, forYou: List<Story>, bbcOn: Boolean,
    refreshing: Boolean, onRefresh: () -> Unit, onOpen: (List<Story>, Int) -> Unit, onSettings: () -> Unit,
) {
    var picked by rememberSaveable { mutableIntStateOf(0) }
    val mode = if (bbcOn) picked else 1 // without BBC there is no Breaking page: Home is just For you
    val headerH = if (bbcOn) 140.dp else 84.dp // title row (+ the Breaking / For you pill when BBC is on) and its fade
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
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = top + headerH, bottom = 130.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (mode == 0) {
                    item { SectionLabel("Breaking now") }
                    itemsIndexed(breaking.take(5)) { i, s -> StoryCard(s, big = i == 0) { onOpen(flat, i) } }
                    item { SectionLabel("Most read") }
                    itemsIndexed(mostRead.take(5)) { i, s -> StoryCard(s, false, rank = i + 1) { onOpen(flat, breaking.take(5).size + i) } }
                } else {
                    item { SectionLabel("Top for you") }
                    if (forYou.isEmpty()) item {
                        Text(
                            if (bbcOn) "Follow topics in My News and they will appear here."
                            else "Nothing to show yet. Add sources (BBC News, local papers, national titles) in Settings > News sources, then follow their sections in My News.",
                            Modifier.padding(4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    itemsIndexed(forYou) { i, s -> StoryCard(s, big = i == 0) { onOpen(flat, i) } }
                }
            }
        }
        EdgeScrim(hz, atTop = true, Modifier.align(Alignment.TopCenter).height(top + headerH))
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = top + 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Firsthand", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onSettings) { Icon(Icons.Filled.Settings, "Settings") }
            }
            if (bbcOn) SlidingPill(hz, 2, mode, Modifier.fillMaxWidth(), height = 46.dp) { i, sel ->
                Box(Modifier.weight(1f).fillMaxHeight().tap { picked = i; view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }, contentAlignment = Alignment.Center) {
                    Text(if (i == 0) "Breaking" else "For you", fontWeight = FontWeight.SemiBold,
                        color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyNewsEditor(hz: HazeState, selected: List<String>, sources: List<Source>, bbcOn: Boolean, onChange: (List<String>) -> Unit, onDone: () -> Unit) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var q by rememberSaveable { mutableStateOf("") }
    val query = q.trim()
    val matches = remember(query, bbcOn) { if (!bbcOn) emptyList() else Categories.catalog.filter { query.isEmpty() || it.name.contains(query, true) || it.group.contains(query, true) } }
    val sourceMatches = remember(query, sources) {
        sources.mapNotNull { s ->
            val feeds = if (query.isEmpty() || s.name.contains(query, true)) s.feeds else s.feeds.filter { it.name.contains(query, true) }
            if (feeds.isEmpty()) null else s to feeds
        }
    }
    val kw = Categories.KW + query.lowercase()
    fun toggle(f: String) = onChange(if (f in selected) selected - f else selected + f)
    fun move(i: Int, d: Int) = onChange(selected.toMutableList().also { it.add(i + d, it.removeAt(i)) })

    LazyColumn(
        Modifier.fillMaxSize().haze(hz),
        contentPadding = PaddingValues(18.dp, top + 16.dp, 18.dp, 130.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Edit My News", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                IconButton(onDone) { Icon(Icons.Filled.Check, "Done") }
            }
            Text("Follow topics or any keyword. Order sets how your sections and For you are arranged.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search topics or follow a keyword") }, shape = RoundedCornerShape(28.dp))
        }
        if (query.isNotEmpty()) {
            item {
                val target = Categories.normalize(kw) // a keyword that names a topic follows the topic
                FilledTonalButton({ toggle(target); q = "" }, Modifier.fillMaxWidth()) {
                    Text(
                        if (target in selected) "Unfollow ${Categories.label(target)}"
                        else if (target != kw) "Follow topic ${Categories.label(target)}" else if (bbcOn) "Follow “$query” (live BBC search)" else "Follow “$query” (matches stories already loaded)",
                    )
                }
            }
            if (matches.isEmpty() && sourceMatches.isEmpty()) item { Text("No matching topics.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
        sourceMatches.forEach { (src, feeds) ->
            item { SectionLabel(src.name) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    feeds.forEach { f -> FilterChip(src.follow(f) in selected, { toggle(src.follow(f)) }, { Text(f.name) }, Modifier.padding(bottom = 4.dp)) }
                }
            }
        }
        if (sources.isEmpty() && query.isEmpty()) item {
            Text("Want local or national papers too? Add publishers in Settings > News sources and their sections appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SearchScreen(
    hz: HazeState, query: String, pool: List<Story>, hits: Pair<String, List<Story>>,
    onSearch: (String) -> Unit, onOpen: (List<Story>, Int) -> Unit,
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val q = query.trim().lowercase()
    LaunchedEffect(query) { onSearch(query) }
    val live = hits.second.takeIf { hits.first == query.trim() && q.length >= 2 }
    val waiting = q.length >= 2 && live == null
    val local = remember(q, pool) { if (q.isEmpty()) pool.take(20) else pool.filter { (it.title + " " + it.summary + " " + it.category).lowercase().contains(q) } }
    // BBC search results first, then anything already loaded that matches.
    val results = remember(live, local) { ((live ?: emptyList()) + local).distinctBy { it.url } }
    LazyColumn(Modifier.fillMaxSize().haze(hz), contentPadding = PaddingValues(14.dp, top + 16.dp, 14.dp, 130.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(
                when { q.isEmpty() -> "Recent stories"; waiting -> "Searching…"; else -> "${results.size} results" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        itemsIndexed(results) { i, s -> StoryCard(s, false) { onOpen(results, i) } }
    }
}

/** My News: followed topics in the user's order (like the BBC app). Pencil opens the editor. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyNewsScreen(
    hz: HazeState, selected: List<String>, topics: Map<String, List<Story>>, sources: List<Source>, bbcOn: Boolean,
    onChange: (List<String>) -> Unit, onOpen: (List<Story>, Int) -> Unit, onSettings: () -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf<String?>(null) } // null = all sections
    if (editing || selected.isEmpty()) {
        MyNewsEditor(hz, selected, sources, bbcOn, onChange) { editing = false }
        return
    }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Follows in the same shared category (BBC Tech + Sky Technology) are one group, merged newest first.
    val groups = remember(selected, topics) { NewsGroups.build(selected, topics) }
    val active = groups.firstOrNull { it.key == filter }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 18.dp, end = 8.dp, top = top + 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("My News", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            IconButton({ editing = true }) { Icon(Icons.Filled.Edit, "Edit My News") }
            IconButton(onSettings) { Icon(Icons.Filled.Settings, "Settings") }
        }
        androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(active == null, { filter = null }, { Text("All") }) }
            items(groups, key = { it.key }) { g -> FilterChip(active?.key == g.key, { filter = g.key }, { Text(g.title) }) }
        }
        LazyColumn(
            Modifier.fillMaxSize().haze(hz),
            contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 130.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val shown = if (active != null) listOf(active) else groups
            shown.forEach { g ->
                val stories = g.stories
                val list = if (active != null) stories else stories.take(4)
                item(key = "h-${g.key}") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(g.title.uppercase(), Modifier.weight(1f).padding(start = 4.dp, top = 8.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        if (active == null && stories.size > 4) TextButton({ filter = g.key }) { Text("See all") }
                    }
                }
                if (list.isEmpty()) item(key = "e-${g.key}") { Text("Nothing loaded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp)) }
                itemsIndexed(list, key = { _, s -> "${g.key}|${s.url}" }) { i, s -> StoryCard(s, big = i == 0 && active != null) { onOpen(list, i) } }
            }
        }
    }
}
