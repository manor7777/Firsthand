package app.firsthand.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.firsthand.AppViewModel
import app.firsthand.data.Categories
import app.firsthand.data.DeepLinks
import app.firsthand.platform.ExternalBrowser
import app.firsthand.data.Story
import dev.chrisbanes.haze.HazeState

/** Full-screen pages opened over the tabs. Kept as a stack so back returns one step at a time (article -> writer -> article). */
private sealed interface Screen {
    val id: Long
    data class Reader(override val id: Long, val list: List<Story>, val index: Int) : Screen
    data class Writer(override val id: Long, val name: String, val role: String?, val source: String?) : Screen
    data class Topic(override val id: Long, val topicId: String, val name: String) : Screen
}

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val hz = remember { HazeState() }
    var picked by rememberSaveable { mutableStateOf<Tab?>(null) } // null until the user taps a tab: then the Settings choice decides
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val accent by vm.accent.collectAsStateWithLifecycle()
    val mode by vm.themeMode.collectAsStateWithLifecycle()
    val barIds by vm.barOrder.collectAsStateWithLifecycle()
    val tipSeen by vm.barTipSeen.collectAsStateWithLifecycle()
    val readAny by vm.readAnySite.collectAsStateWithLifecycle()
    val aOn by vm.alertEnabled.collectAsStateWithLifecycle()
    val aFollows by vm.alertOnlyFollows.collectAsStateWithLifecycle()
    val aMode by vm.alertMode.collectAsStateWithLifecycle()
    val aInt by vm.alertInterval.collectAsStateWithLifecycle()

    val stack = remember { mutableStateListOf<Screen>() }
    var nextId by remember { mutableLongStateOf(0L) }
    fun push(make: (Long) -> Screen) { stack.add(make(nextId++)) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }

    val pending by vm.pendingOpen.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        pending?.let { s -> searching = false; settingsOpen = false; push { Screen.Reader(it, listOf(s), 0) }; vm.pendingOpen.value = null }
    }

    val home by vm.home.collectAsStateWithLifecycle()
    val forYou by vm.forYou.collectAsStateWithLifecycle()
    val topics by vm.topicStories.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val pool by vm.searchPool.collectAsStateWithLifecycle()
    val hits by vm.searchHits.collectAsStateWithLifecycle()
    val blocks by vm.blocks.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val articleTopics by vm.articleTopics.collectAsStateWithLifecycle()
    val articleInfo by vm.articleInfo.collectAsStateWithLifecycle()
    val pages by vm.pages.collectAsStateWithLifecycle()
    val blocked by vm.blockedAuthors.collectAsStateWithLifecycle()
    val blockedTerms by vm.blockedTerms.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val bbcOn by vm.bbcEnabled.collectAsStateWithLifecycle()
    val bar = barIds.mapNotNull(Tab::of)
    // The home page is the first button that is a page (the second one when Search comes first).
    val homeTab = Tab.of(app.firsthand.data.BarOrder.home(barIds)) ?: Tab.ForYou
    val tab = picked?.takeIf { it != Tab.Search && it in bar } ?: homeTab
    val aBreaking by vm.alertBreaking.collectAsStateWithLifecycle()
    val aOff by vm.alertOffSources.collectAsStateWithLifecycle()
    val aCats by vm.alertCategories.collectAsStateWithLifecycle()
    val aKeywords by vm.alertKeywords.collectAsStateWithLifecycle()
    val qOn by vm.quietEnabled.collectAsStateWithLifecycle()
    val qStart by vm.quietStart.collectAsStateWithLifecycle()
    val qEnd by vm.quietEnd.collectAsStateWithLifecycle()
    // Alerts can cover BBC News and the added catalogue sources (feeds added by address have no breaking signal worth trusting).
    val alertSources = buildList { if (bbcOn) add("bbc" to "BBC News"); sources.filter { !it.custom }.forEach { add(it.id to it.name) } }
    val alerts = AlertState(
        enabled = aOn, onlyFollows = aFollows, mode = aMode, interval = aInt,
        setEnabled = vm::setAlertEnabled, setOnlyFollows = vm::setAlertOnlyFollows, setMode = vm::setAlertMode, setInterval = vm::setAlertInterval, sendTest = vm::sendTestAlert,
        breaking = aBreaking, setBreaking = vm::setAlertBreaking,
        sources = alertSources, offSources = aOff.toSet(), toggleSource = vm::toggleAlertSource,
        categories = aCats.toSet(), toggleCategory = vm::toggleAlertCategory,
        keywords = aKeywords, addKeyword = vm::addAlertKeyword, removeKeyword = vm::removeAlertKeyword,
        quietEnabled = qOn, quietStart = qStart, quietEnd = qEnd, setQuiet = vm::setQuiet,
    )
    // Pills naming each story's source only make sense once there is more than one source to tell apart.
    val showSource = (if (bbcOn) 1 else 0) + sources.size > 1
    val videos by vm.videos.collectAsStateWithLifecycle()
    val upState by vm.update.collectAsStateWithLifecycle()
    val installResult by vm.installResult.collectAsStateWithLifecycle()
    val updates = UpdateUi(vm.installedName, upState, installResult, vm::checkForUpdate, vm::installUpdate)
    val open: (List<Story>, Int) -> Unit = { l, i -> push { Screen.Reader(it, l, i) } }

    BackHandler(enabled = searching && stack.isEmpty()) { searching = false; query = "" }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    val openLink: (String) -> Unit = { url ->
        val link = DeepLinks.parse(url)?.takeIf { bbcOn && it !is DeepLinks.Link.Unsupported }
        if (link != null) vm.openLink(link) else ExternalBrowser.open(ctx, url)
    }
    CompositionLocalProvider(LocalShowSource provides showSource, LocalOpenLink provides openLink) {
    Box(Modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            when {
                searching -> SearchScreen(hz, query, pool, hits, vm::search, open)
                tab == Tab.MyNews -> MyNewsScreen(hz, cats, topics, sources, bbcOn, vm::setCategories, open) { settingsOpen = true }
                else -> HomeScreen(hz, home.breaking, home.mostRead, forYou, bbcOn, tab == Tab.Breaking, refreshing, vm::refresh, open) { settingsOpen = true }
            }
        }
        // The fade behind the bar: just taller than the bar. While the keyboard is up the bar rides above it, and so does the fade.
        val imeUp = searching && WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp
        EdgeScrim(hz, atTop = false, Modifier.align(Alignment.BottomCenter).then(
            if (imeUp) Modifier.imePadding().height(120.dp) else Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 120.dp)))
        GlassDock(
            hz, bar, tab, searching, query,
            onTab = { picked = it }, onSearch = { searching = true }, onReorder = { l -> vm.setBarOrder(l.map { it.id }) },
            onExit = { searching = false; query = "" }, onQuery = { query = it },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        if (!tipSeen && !searching && !settingsOpen && stack.isEmpty()) BarTip(vm::dismissBarTip)
        // Every page in the stack stays composed (so going back keeps scroll/swipe position); only the top one handles back.
        stack.forEachIndexed { i, sc ->
            key(sc.id) {
                val isTop = i == stack.lastIndex
                val shown = remember { MutableTransitionState(false) }.apply { targetState = true }
                AnimatedVisibility(shown, enter = slideInHorizontally { it }) {
                    when (sc) {
                        is Screen.Reader -> ArticlePager(
                            sc.list, sc.index, blocks, vm::loadArticle, live, vm::loadLive, articleTopics, cats,
                            VideoSupport(videos, vm::loadVideo, vm::videoStream), articleInfo,
                            onAuthor = { a -> push { Screen.Writer(it, a.name, a.role, a.source) } },
                            onTopic = { t -> push { Screen.Topic(it, t.id, t.name) } },
                            isTop = isTop, onClose = ::pop, onBlockKeyword = vm::addBlockedTerm,
                        )
                        is Screen.Writer -> {
                            val key = if (sc.source != null) Categories.xauthorFollow(sc.name) else Categories.authorFollow(sc.name)
                            LaunchedEffect(key) { vm.loadPage(key) }
                            val isBlocked = blocked.any { app.firsthand.data.Author.key(it) == app.firsthand.data.Author.key(sc.name) }
                            StoryListPage(
                                title = sc.name, subtitle = sc.role ?: (sc.source?.let { "$it writer" } ?: "BBC writer"), stories = pages[key],
                                loadingText = "Finding articles by ${sc.name}…",
                                emptyText = if (sc.source != null) "No other recent articles by ${sc.name} in the feeds you follow. Follow more of ${sc.source}'s sections in My News to see more."
                                else "No articles by ${sc.name} found yet. BBC search mostly covers recent stories; pull down to try again.",
                                followed = key in cats, onFollow = { vm.toggleFollow(key) },
                                block = BlockControl(isBlocked, { vm.blockAuthor(sc.name) }, { vm.unblockAuthor(sc.name) }),
                                onRefresh = { vm.loadPage(key, force = true) }, onOpen = open, isTop = isTop, onBack = ::pop,
                            )
                        }
                        is Screen.Topic -> {
                            val key = Categories.topicFollow(sc.topicId, sc.name)
                            LaunchedEffect(key) { vm.loadPage(key) }
                            StoryListPage(
                                title = sc.name, subtitle = "BBC topic", stories = pages[key],
                                loadingText = "Loading ${sc.name}…", emptyText = "Nothing in this topic right now. Pull down to try again.",
                                followed = key in cats, onFollow = { vm.toggleFollow(key) }, block = null,
                                onRefresh = { vm.loadPage(key, force = true) }, onOpen = open, isTop = isTop, onBack = ::pop,
                            )
                        }
                    }
                }
            }
        }
        AnimatedVisibility(settingsOpen, enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }) {
            SettingsScreen(accent, mode, vm::setAccent, vm::setThemeMode, vm::resetBarOrder, vm::exportJson, vm::importJson, alerts, updates,
                BlockedUi(blocked, vm::unblockAuthor, blockedTerms, vm::addBlockedTerm, vm::removeBlockedTerm), SourcesUi(sources, vm::addSource, vm::addCustomSource, vm::removeSource, bbcOn, vm::setBbcEnabled, readAny, vm::setReadAnySite)) { settingsOpen = false }
        }
    }
    }
}
