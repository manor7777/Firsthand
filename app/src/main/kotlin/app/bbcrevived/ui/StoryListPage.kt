package app.bbcrevived.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.bbcrevived.data.Story

/** Block/unblock control shown on writer pages. */
class BlockControl(val blocked: Boolean, val block: () -> Unit, val unblock: () -> Unit)

/**
 * A writer's or topic's page: title, Follow (and Block for writers), then their stories newest first.
 * [stories] null = still loading. Tapping a story opens the reader on this list (swipe between them).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StoryListPage(
    title: String, subtitle: String, stories: List<Story>?, loadingText: String, emptyText: String,
    followed: Boolean, onFollow: () -> Unit, block: BlockControl?,
    onRefresh: () -> Unit, onOpen: (List<Story>, Int) -> Unit, isTop: Boolean, onBack: () -> Unit,
) {
    BackHandler(enabled = isTop, onBack = onBack)
    var confirmBlock by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (block?.blocked != true) {
                    if (followed) FilledTonalButton(onFollow) { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Following") }
                    else Button(onFollow) { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Follow") }
                }
                block?.let { b ->
                    if (b.blocked) FilledTonalButton(b.unblock) { Text("Unblock") }
                    else OutlinedButton({ confirmBlock = true }) { Icon(Icons.Filled.Block, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Block") }
                }
            }
            if (block?.blocked == true) Text(
                "You've blocked $title. Their articles, including ones they co-wrote, are hidden everywhere in the app. Unblock here or in Settings.",
                Modifier.padding(horizontal = 20.dp, vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
            ) else PullToRefreshBox(stories == null, onRefresh, Modifier.fillMaxSize()) {
                when {
                    stories == null -> Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(loadingText, Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    stories.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) { // scrollable so pull-to-refresh still works
                        item { Text(emptyText, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(14.dp, 4.dp, 14.dp, 32.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(stories, key = { _, s -> s.url }) { i, s -> StoryCard(s, big = i == 0) { onOpen(stories, i) } }
                    }
                }
            }
        }
    }
    if (confirmBlock && block != null) AlertDialog(
        onDismissRequest = { confirmBlock = false },
        title = { Text("Block $title?") },
        text = { Text("Articles they wrote or co-wrote will be hidden everywhere, and you won't get alerts for them. You can unblock any time in Settings.") },
        confirmButton = { TextButton({ confirmBlock = false; block.block() }) { Text("Block") } },
        dismissButton = { TextButton({ confirmBlock = false }) { Text("Cancel") } },
    )
}
