package app.bbcrevived.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.bbcrevived.AppViewModel
import app.bbcrevived.data.Story
import dev.chrisbanes.haze.HazeState

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val hz = remember { HazeState() }
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var reader by remember { mutableStateOf<Pair<List<Story>, Int>?>(null) }

    val home by vm.home.collectAsStateWithLifecycle()
    val forYou by vm.forYou.collectAsStateWithLifecycle()
    val topics by vm.topicStories.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val pool by vm.searchPool.collectAsStateWithLifecycle()
    val blocks by vm.blocks.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val open: (List<Story>, Int) -> Unit = { l, i -> reader = l to i }

    BackHandler(enabled = searching && reader == null) { searching = false; query = "" }

    Box(Modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            when {
                searching -> SearchScreen(hz, query, pool, open)
                tab == Tab.Home -> HomeScreen(hz, home.breaking, home.mostRead, forYou, refreshing, vm::refresh, open)
                else -> MyNewsScreen(hz, cats, topics, vm::setCategories, open)
            }
        }
        GlassDock(
            hz, tab, searching, query,
            onTab = { tab = it }, onSearch = { searching = true },
            onExit = { searching = false; query = "" }, onQuery = { query = it },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        AnimatedVisibility(reader != null, enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }) {
            reader?.let { (l, i) -> ArticlePager(l, i, blocks, vm::loadArticle, live, vm::loadLive) { reader = null } }
        }
    }
}
