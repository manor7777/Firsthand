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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import app.bbcrevived.data.Block
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
    load: (Story) -> Unit, live: Map<String, List<LivePost>>, loadLive: (Story) -> Unit, onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val hz = remember { HazeState() }
    val pager = rememberPagerState(start) { stories.size }
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            HorizontalPager(pager, Modifier.fillMaxSize().haze(hz), beyondViewportPageCount = 1) { p ->
                val s = stories[p]
                LaunchedEffect(s.url) { load(s) }
                if (s.isLive) LivePage(s, live[s.url], loadLive, pager.currentPage == p) else ArticlePage(s, blocks[s.url])
            }
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClose, Modifier.glass(hz)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Spacer(Modifier.weight(1f))
                Text("${pager.currentPage + 1} / ${stories.size}", Modifier.glass(hz).padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ArticlePage(s: Story, blocks: List<Block>?) {
    val uri = LocalUriHandler.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
        item { s.imageUrl?.let { AsyncImage(it, null, Modifier.fillMaxWidth().height(260.dp), contentScale = ContentScale.Crop) } ?: Spacer(Modifier.height(72.dp)) }
        item {
            Column(Modifier.padding(20.dp, 18.dp, 20.dp, 4.dp)) {
                Text(s.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(s.category, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }
        when {
            blocks == null -> item { Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() } }
            blocks.isEmpty() -> item {
                // Parser found nothing (page format changed or offline): show the RSS summary and a link out.
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(s.summary, style = MaterialTheme.typography.bodyLarge)
                    Text("Full text isn't available in-app for this story.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton({ uri.openUri(s.url) }) { Text("Open original") }
                }
            }
            else -> {
                // The hero already shows the lead photo; drop the same photo from the body (matched by file, any size).
                fun key(u: String) = u.substringAfter("/standard/", u).substringAfter('/').substringBefore('.')
                val heroKey = s.imageUrl?.let(::key)
                val dup = if (heroKey == null) null else blocks.withIndex().firstOrNull { (i, b) -> i < 4 && b is Block.Image && key(b.url) == heroKey }
                val body = if (dup == null) blocks else blocks.filterIndexed { i, _ -> i != dup.index }
                (dup?.value as? Block.Image)?.caption?.let { cap ->
                    item { Text(cap, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(body) { b -> BlockView(b) }
            }
        }
    }
}

@Composable
private fun BlockView(b: Block) {
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
