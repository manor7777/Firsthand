package app.firsthand.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import app.firsthand.data.Story
import app.firsthand.data.VideoInfo
import coil.compose.AsyncImage

/** What the reader needs to show /news/videos pages. [videos] maps URL -> details (null value = couldn't be read). */
class VideoSupport(
    val videos: Map<String, VideoInfo?>,
    val load: (Story) -> Unit,
    val stream: suspend (String) -> String?,
)

private sealed interface Play {
    data object Idle : Play
    data object Finding : Play
    data class Native(val hls: String) : Play
    /** BBC wouldn't give us a stream (e.g. outside the UK): use the page's own player, inline. */
    data object Page : Play
}

/** Native video page: player on top, then headline, write-up and topics. Playback stops when swiped away. */
@Composable
fun VideoPage(s: Story, support: VideoSupport, visible: Boolean, follows: List<String>, onTopic: (app.firsthand.data.TopicRef) -> Unit) {
    val loaded = support.videos.containsKey(s.url)
    val info = support.videos[s.url]
    var play by remember(s.url) { mutableStateOf<Play>(Play.Idle) }
    LaunchedEffect(visible) { if (!visible && play !is Play.Idle) play = Play.Idle }
    LaunchedEffect(play) {
        if (play == Play.Finding) {
            val hls = info?.versionId?.let { support.stream(it) }
            play = if (hls != null) Play.Native(hls) else Play.Page
        }
    }
    val portrait = info?.portrait == true

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 72.dp, bottom = 48.dp)) {
        item {
            // Exact size from the screen: landscape 16:9 full width; portrait 9:16 capped to 60% of the screen height.
            BoxWithConstraints(Modifier.fillMaxWidth().background(Color.Black).clipToBounds(), contentAlignment = Alignment.Center) {
                val screenH = LocalConfiguration.current.screenHeightDp.dp
                val frame = if (portrait) {
                    val h = minOf(maxWidth * 16f / 9f, screenH * 0.6f)
                    Modifier.size(h * 9f / 16f, h)
                } else Modifier.size(maxWidth, maxWidth * 9f / 16f)
                when (val p = play) {
                    is Play.Native -> HlsPlayer(p.hls, frame) { play = Play.Page }
                    Play.Page -> Box(Modifier.fillMaxWidth().height(maxWidth * 9f / 16f + 120.dp)) { InlineEmbed(s.url) }
                    else -> Box(frame.clickable(enabled = info != null || loaded) { play = if (info?.versionId != null) Play.Finding else Play.Page }, Alignment.Center) {
                        (info?.poster ?: s.imageUrl)?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        Box(Modifier.size(72.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), Alignment.Center) {
                            if (p == Play.Finding || !loaded) CircularProgressIndicator(Modifier.size(32.dp), color = Color.White, strokeWidth = 3.dp)
                            else Icon(Icons.Filled.PlayArrow, "Play video", Modifier.size(44.dp), tint = Color.White)
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(20.dp, 18.dp, 20.dp, 4.dp)) {
                Text(info?.title ?: s.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                val dur = info?.durationSec?.let { " · %d:%02d".format(it / 60, it % 60) }.orEmpty()
                Text("Video$dur", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (info != null) items(info.summary) { BlockView(it) }
        else if (loaded && s.summary.isNotBlank()) item { Text(s.summary, Modifier.padding(horizontal = 20.dp, vertical = 6.dp), style = MaterialTheme.typography.bodyLarge) }
        if (!info?.topics.isNullOrEmpty()) item { TopicChips(info!!.topics, follows, onTopic) }
    }
}

/** Media3 HLS player; reports playback errors so the caller can fall back to BBC's page player. */
@Composable
private fun HlsPlayer(url: String, modifier: Modifier, onError: () -> Unit) {
    val ctx = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build())
            prepare(); playWhenReady = true
        }
    }
    DisposableEffect(player) {
        val l = object : Player.Listener { override fun onPlayerError(error: PlaybackException) { onError() } }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }
    AndroidView(
        { c -> (android.view.LayoutInflater.from(c).inflate(app.firsthand.R.layout.player_view, null) as PlayerView).apply { this.player = player } },
        modifier.clipToBounds(),
    )
}
