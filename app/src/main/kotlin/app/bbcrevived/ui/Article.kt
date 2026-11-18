package app.bbcrevived.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import app.bbcrevived.platform.ExternalBrowser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import app.bbcrevived.data.Block
import app.bbcrevived.data.Author
import app.bbcrevived.data.ArticleInfo
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import app.bbcrevived.data.Categories
import app.bbcrevived.data.DeepLinks
import app.bbcrevived.data.TopicRef
import app.bbcrevived.data.LivePost
import kotlinx.coroutines.delay
import app.bbcrevived.data.Story
import coil.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

/** Native article reader: swipe left/right between stories in the list you opened it from. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArticlePager(
    stories: List<Story>, start: Int, blocks: Map<String, List<Block>>,
    load: (Story) -> Unit, live: Map<String, List<LivePost>>, loadLive: (Story) -> Unit,
    topics: Map<String, List<TopicRef>>, follows: List<String>,
    video: VideoSupport, info: Map<String, ArticleInfo>, onAuthor: (Author) -> Unit, onTopic: (TopicRef) -> Unit,
    isTop: Boolean, onClose: () -> Unit,
) {
    BackHandler(enabled = isTop, onBack = onClose)
    val hz = remember { HazeState() }
    val pager = rememberPagerState(start) { stories.size }
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            HorizontalPager(pager, Modifier.fillMaxSize().haze(hz), beyondViewportPageCount = 1) { p ->
                val s = stories[p]
                LaunchedEffect(s.url) { if (DeepLinks.parse(s.url) is DeepLinks.Link.Video) video.load(s) else load(s) }
                when {
                    s.isLive -> LivePage(s, live[s.url], loadLive, pager.currentPage == p)
                    DeepLinks.parse(s.url) is DeepLinks.Link.Video ->
                        VideoPage(s, video, pager.currentPage == p && isTop, follows, onTopic)
                    else -> ArticlePage(s, blocks[s.url], topics[s.url].orEmpty(), follows, info[s.url], onAuthor, onTopic)
                }
            }
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClose, Modifier.glass(hz)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Spacer(Modifier.weight(1f))
                Text("${pager.currentPage + 1} / ${stories.size}", Modifier.glass(hz).padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArticlePage(
    s: Story, blocks: List<Block>?, topics: List<TopicRef>, follows: List<String>,
    info: ArticleInfo?, onAuthor: (Author) -> Unit, onTopic: (TopicRef) -> Unit,
) {
    val ctx = LocalContext.current
    // The lead photo usually appears twice: as the story's image (feed thumbnail, or BBC's "BBC NEWS"-branded share image
    // for links) and as the first body image. Match them by file ID and show it once, preferring the clean body copy.
    val dup = leadDuplicate(s.imageUrl, blocks)
    val hero = (dup?.value as? Block.Image)?.url ?: s.imageUrl
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
        item { hero?.let { AsyncImage(it, null, Modifier.fillMaxWidth().height(260.dp), contentScale = ContentScale.Crop) } ?: Spacer(Modifier.height(72.dp)) }
        item {
            Column(Modifier.padding(20.dp, 18.dp, 20.dp, 4.dp)) {
                Text(s.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(s.category, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                info?.authors?.forEach { a -> BylineRow(a) { onAuthor(a) } }
                dateLine(info)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp)) }
            }
        }
        when {
            blocks == null -> item { Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() } }
            blocks.isEmpty() -> item {
                // Parser found nothing (page format changed or offline): show the RSS summary and a link out.
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(s.summary, style = MaterialTheme.typography.bodyLarge)
                    Text("Full text isn't available in-app for this story.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton({ ExternalBrowser.open(ctx, s.url) }) { Text("Open original") }
                }
            }
            else -> {
                val body = if (dup == null) blocks else blocks.filterIndexed { i, _ -> i != dup.index }
                (dup?.value as? Block.Image)?.caption?.let { cap ->
                    item { Text(cap, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(body) { b -> BlockView(b) }
            }
        }
        if (topics.isNotEmpty()) item { TopicChips(topics, follows, onTopic) }
    }
}

/** BBC image file ID: the last path segment without extensions, the same across sizes, .webp copies and branded variants. */
internal fun imageKey(u: String) = u.substringBefore('?').substringAfterLast('/').substringBefore('.')

/** The body image (in the first few blocks) that is the same photo as the story's lead image, if any. */
internal fun leadDuplicate(lead: String?, blocks: List<Block>?): IndexedValue<Block>? {
    val k = lead?.let(::imageKey)?.takeIf { it.length >= 8 } ?: return null
    return blocks?.withIndex()?.firstOrNull { (i, b) -> i < 4 && b is Block.Image && imageKey(b.url) == k }
}

/** Topic tags: tap opens the topic's page (follow from there); a tick marks topics you already follow. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TopicChips(topics: List<TopicRef>, follows: List<String>, onOpen: (TopicRef) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("RELATED TOPICS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            topics.forEach { t ->
                val followed = Categories.topicFollow(t.id, t.name) in follows
                if (followed) AssistChip(
                    onClick = { onOpen(t) }, label = { Text(t.name) }, modifier = Modifier.padding(bottom = 4.dp),
                    leadingIcon = { Icon(Icons.Filled.Check, "Following", Modifier.size(18.dp)) },
                ) else AssistChip(onClick = { onOpen(t) }, label = { Text(t.name) }, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

/** "Michael Race · Business reporter": tappable, opens that writer's page. */
@Composable
internal fun BylineRow(a: Author, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.padding(top = 12.dp).fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(a.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                a.role?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Articles by ${a.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "18 Nov 2026, 07:15 · Updated 7 hours ago" in the phone's time zone; null without dates. */
internal fun dateLine(info: ArticleInfo?): String? {
    fun instant(s: String?) = s?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() }
    val p = instant(info?.published) ?: return null
    val u = instant(info?.updated)
    val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(java.time.ZoneId.systemDefault())
    val updated = if (u != null && java.time.Duration.between(p, u).toMinutes() >= 5) " · Updated ${relative(u)}" else ""
    return fmt.format(p) + updated
}

private fun relative(t: java.time.Instant): String {
    val m = java.time.Duration.between(t, java.time.Instant.now()).toMinutes().coerceAtLeast(0)
    return when {
        m < 1 -> "just now"
        m < 60 -> "$m min ago"
        m < 1440 -> "${m / 60} hour${if (m / 60 == 1L) "" else "s"} ago"
        else -> "${m / 1440} day${if (m / 1440 == 1L) "" else "s"} ago"
    }
}

@Composable
internal fun BlockView(b: Block) {
    val pad = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
    when (b) {
        is Block.Paragraph -> Text(b.text, pad, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2f))
        is Block.Heading -> Text(b.text, pad.padding(top = 10.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        is Block.Quote -> Text(b.text, pad, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        is Block.Image -> AsyncImage(b.url, b.caption, Modifier.fillMaxWidth().padding(vertical = 8.dp), contentScale = ContentScale.FillWidth)
        is Block.Embed -> InlineEmbed(b.url)
    }
}

/** Borderless inline WebView for interactive/unknown blocks. Best-effort: not every BBC widget will work. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InlineEmbed(url: String) {
    AndroidView(
        factory = { c -> WebView(c).apply { settings.javaScriptEnabled = true; settings.domStorageEnabled = true; setBackgroundColor(0); loadUrl(url) } },
        modifier = Modifier.fillMaxWidth().height(420.dp).padding(vertical = 8.dp),
    )
}

/** Native live timeline: pinned posts first, newest next, re-polled every 30s while this page is on screen. */
@Composable
private fun LivePage(s: Story, posts: List<LivePost>?, loadLive: (Story) -> Unit, visible: Boolean) {
    LaunchedEffect(s.url, visible) {
        if (!visible) return@LaunchedEffect
        while (true) { loadLive(s); delay(30_000) }
    }
    when {
        posts == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        posts.isEmpty() -> Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(72.dp))
            Text(s.title, Modifier.padding(20.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            InlineEmbed(s.url) // format not recognised: show the live page itself, inline
        }
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 80.dp, bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(s.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("LIVE · updates every 30 s", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
            }
            items(posts, key = { it.id }) { p ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(14.dp)) {
                        Text((if (p.pinned) "PINNED · " else "") + (if (p.breaking) "BREAKING · " else "") + (p.time ?: ""), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        p.title?.let { Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp)) }
                        p.blocks.forEach { b -> BlockView(b) }
                    }
                }
            }
        }
    }
}
