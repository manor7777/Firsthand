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
import app.bbcrevived.data.Story
import coil.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

/** Native article reader: swipe left/right between stories in the list you opened it from. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArticlePager(
    stories: List<Story>, start: Int, blocks: Map<String, List<Block>>,
    load: (Story) -> Unit, onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val hz = remember { HazeState() }
    val pager = rememberPagerState(start) { stories.size }
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            HorizontalPager(pager, Modifier.fillMaxSize().haze(hz), beyondViewportPageCount = 1) { p ->
                val s = stories[p]
                LaunchedEffect(s.url) { load(s) }
                ArticlePage(s, blocks[s.url])
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
            else -> items(blocks) { b -> BlockView(b) }
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
