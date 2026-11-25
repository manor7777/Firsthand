package app.firsthand.ui

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
import app.firsthand.platform.ExternalBrowser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import app.firsthand.data.Block
import app.firsthand.data.Author
import app.firsthand.data.ArticleInfo
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import app.firsthand.data.Categories
import app.firsthand.data.DeepLinks
import app.firsthand.data.TopicRef
import app.firsthand.data.LivePost
import kotlinx.coroutines.delay
import app.firsthand.data.Story
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import app.firsthand.data.BlockScope
import app.firsthand.data.Blocking
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
    isTop: Boolean, onClose: () -> Unit, onBlockKeyword: (String, BlockScope) -> String?,
) {
    BackHandler(enabled = isTop, onBack = onClose)
    val hz = remember { HazeState() }
    val pager = rememberPagerState(start) { stories.size }
    val ctx = LocalContext.current
    val view = LocalView.current
    // Selected text can be copied, shared or blocked as a keyword; the menu hands the text here.
    var blockText by remember { mutableStateOf<String?>(null) }
    val toolbar = remember(view) { FirsthandTextToolbar(view) { blockText = it } }
    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            HorizontalPager(pager, Modifier.fillMaxSize().haze(hz), beyondViewportPageCount = 1) { p ->
                val s = stories[p]
                LaunchedEffect(s.url) { if (DeepLinks.parse(s.url) is DeepLinks.Link.Video) video.load(s) else load(s) }
                CompositionLocalProvider(LocalPageVisible provides (pager.currentPage == p && isTop), LocalPageUrl provides s.url) {
                SelectionContainer {
                    when {
                        s.isLive -> LivePage(s, live[s.url], loadLive, pager.currentPage == p)
                        DeepLinks.parse(s.url) is DeepLinks.Link.Video ->
                            VideoPage(s, video, pager.currentPage == p && isTop, follows, onTopic)
                        else -> ArticlePage(s, blocks[s.url], topics[s.url].orEmpty(), follows, info[s.url], onAuthor, onTopic)
                    }
                }
                }
            }
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClose, Modifier.glass(hz)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Spacer(Modifier.weight(1f))
                Box(Modifier.height(40.dp).glass(hz).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Text("${pager.currentPage + 1} / ${stories.size}", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(40.dp).glass(hz).tap { shareStory(ctx, stories[pager.currentPage.coerceIn(0, stories.lastIndex)]) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Share, "Share link", Modifier.size(20.dp))
                }
            }
        }
    }
    }
    blockText?.let { raw ->
        val term = Blocking.clean(raw)
        var scope by remember(raw) { mutableStateOf(BlockScope.Subject) }
        var error by remember(raw) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { blockText = null },
            title = { Text(if (term != null) "Block \u201C$term\u201D?" else "Can't block that") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (term == null) Text("Select a word or short phrase (2 to 60 characters).")
                    else {
                        Text("Hide stories about it:")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BlockScope.entries.forEach { sc -> FilterChip(scope == sc, { scope = sc }, { Text(sc.label) }) }
                        }
                        Text(
                            if (scope == BlockScope.Subject) "Subject: it's in the headline or topic tags, so the story is about it."
                            else "Story: it appears anywhere in the headline, summary or tags, even in passing.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            confirmButton = {
                if (term != null) TextButton({
                    val message = onBlockKeyword(term, scope)
                    if (message == null) { blockText = null; Toast.makeText(ctx, "Blocked \u201C$term\u201D (see Settings > Blocked keywords)", Toast.LENGTH_LONG).show() }
                    else error = message
                }) { Text("Block") }
            },
            dismissButton = { TextButton({ blockText = null }) { Text("Cancel") } },
        )
    }
}

/** Shares the story's headline and link through Android's share sheet. */
private fun shareStory(ctx: Context, s: Story) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, s.title).putExtra(Intent.EXTRA_TEXT, "${s.title}\n${s.url}")
    runCatching { ctx.startActivity(Intent.createChooser(send, null)) }
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
    val lead = blocks?.firstOrNull() as? Block.Video
    val hero = if (lead != null) null else (dup?.value as? Block.Image)?.url ?: s.imageUrl
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
        item {
            if (lead != null) { Spacer(Modifier.height(readerTopInset())); VideoBlockView(lead, s.url) }
            else hero?.let { AsyncImage(it, null, Modifier.fillMaxWidth().height(260.dp), contentScale = ContentScale.Crop) } ?: Spacer(Modifier.height(readerTopInset()))
        }
        item {
            Column(Modifier.padding(20.dp, 18.dp, 20.dp, 4.dp)) {
                Text(s.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(listOf(s.sourceName, s.sectionName).filter { it.isNotBlank() }.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
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
                val body = if (lead != null) blocks.drop(1) else if (dup == null) blocks else blocks.filterIndexed { i, _ -> i != dup.index }
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
    if (lead.isNullOrBlank() || blocks == null) return null
    val k = imageKey(lead).takeIf { it.length >= 8 }
    val byId = k?.let { blocks.withIndex().firstOrNull { (i, b) -> i < 4 && b is Block.Image && imageKey(b.url) == k } }
    if (byId != null) return byId
    // Some stories carry the same photo under a different file ID in the feed and the page. A body that opens
    // straight with an image is BBC's lead photo again, so treat it as the duplicate.
    return blocks.firstOrNull()?.takeIf { it is Block.Image }?.let { IndexedValue(0, it) }
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

/** Opens a tapped link inside the reader (BBC pages) or the browser; provided by AppRoot. */
internal val LocalPageUrl = compositionLocalOf { "" }
internal val LocalOpenLink = compositionLocalOf<(String) -> Unit> { {} }

@Composable
internal fun BlockView(b: Block) {
    val pad = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
    when (b) {
        is Block.Paragraph -> Text(b.text, pad, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2f))
        is Block.Link -> {
            val open = LocalOpenLink.current
            Text(
                b.text, pad.clickable { open(b.url) }, color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2f, textDecoration = TextDecoration.Underline),
            )
        }
        is Block.Heading -> Text(b.text, pad.padding(top = 10.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        is Block.Quote -> Text(b.text, pad, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        is Block.Image -> AsyncImage(b.url, b.caption, Modifier.fillMaxWidth().padding(vertical = 8.dp), contentScale = ContentScale.FillWidth)
        is Block.Embed -> if (b.url.startsWith("https://www.instagram.com/")) InstagramEmbed(b.url) else InlineEmbed(b.url)
        is Block.Video -> VideoBlockView(b, LocalPageUrl.current)
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

/** An Instagram post (its /embed/ page) inline; taps on anything but the post itself open outside the frame. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun InstagramEmbed(url: String) {
    val open = LocalOpenLink.current
    AndroidView(
        factory = { c ->
            WebView(c).apply {
                settings.javaScriptEnabled = true; settings.domStorageEnabled = true; setBackgroundColor(0)
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                        val u = request.url.toString()
                        if (u.startsWith(url.substringBefore("/embed/"))) return false
                        if (request.hasGesture()) open(u)
                        return true
                    }
                }
                loadUrl(url)
            }
        },
        modifier = Modifier.fillMaxWidth().height(640.dp).padding(vertical = 8.dp),
    )
}

/**
 * Space taken by the reader's floating back / counter / share buttons: the status bar (which varies by phone) plus the 48 dp
 * buttons and their padding. A page with no lead image starts below this, so its title never sits under the buttons.
 */
@Composable
internal fun readerTopInset(): androidx.compose.ui.unit.Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 72.dp

/** Native live timeline: pinned posts first, newest next, re-polled every 30s while this page is on screen. */
@Composable
private fun LivePage(s: Story, posts: List<LivePost>?, loadLive: (Story) -> Unit, visible: Boolean) {
    val ctx = LocalContext.current
    LaunchedEffect(s.url, visible) {
        if (!visible) return@LaunchedEffect
        while (true) { loadLive(s); delay(30_000) }
    }
    when {
        posts == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        posts.isEmpty() -> Column(Modifier.fillMaxSize().padding(top = readerTopInset()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Format not recognised or nothing posted yet: keep it native (no website inside the app) and offer the original.
            Text(s.title, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (s.summary.isNotBlank()) Text(s.summary, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodyLarge)
            Text("Live updates can't be shown in the app right now.", Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton({ ExternalBrowser.open(ctx, s.url) }, Modifier.padding(horizontal = 20.dp)) { Text("Open original") }
        }
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = readerTopInset(), bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
