package app.bbcrevived

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.bbcrevived.data.*
import app.bbcrevived.notify.Alerts
import app.bbcrevived.update.Installer
import app.bbcrevived.update.UpdateChecker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    private val prefs = Prefs(app)

    val categories = prefs.categories.stateIn(viewModelScope, SharingStarted.Eagerly, listOf("World", "Tech", "Sport", "Science"))
    val accent = prefs.accent.stateIn(viewModelScope, SharingStarted.Eagerly, "dynamic")
    val themeMode = prefs.theme.stateIn(viewModelScope, SharingStarted.Eagerly, "system")
    fun setAccent(v: String) { viewModelScope.launch { prefs.setAccent(v) } }
    fun setThemeMode(v: String) { viewModelScope.launch { prefs.setTheme(v) } }

    /** Writers the user has blocked (display names). Their articles, and any they co-wrote, are hidden everywhere. */
    val blockedAuthors = prefs.blockedAuthors.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _bylines = MutableStateFlow<Map<String, List<Author>>>(emptyMap())
    /** URLs of articles credited (even partly) to a blocked writer. */
    private val hidden: StateFlow<Set<String>> = combine(blockedAuthors, _bylines) { b, by ->
        val keys = b.map { Author.key(it) }.toSet()
        if (keys.isEmpty()) emptySet() else by.filterValues { a -> a.any { Author.key(it.name) in keys } }.keys
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    private fun List<Story>.visible(h: Set<String>) = if (h.isEmpty()) this else filterNot { it.url in h }

    fun isBlocked(name: String) = blockedAuthors.value.any { Author.key(it) == Author.key(name) }
    fun blockAuthor(name: String) {
        viewModelScope.launch {
            prefs.setBlockedAuthors((blockedAuthors.value + name.trim()).distinctBy { Author.key(it) })
            val f = Categories.authorFollow(name)
            if (f in categories.value) prefs.set(categories.value - f) // blocking someone also unfollows them
        }
    }
    fun unblockAuthor(name: String) {
        viewModelScope.launch { prefs.setBlockedAuthors(blockedAuthors.value.filterNot { Author.key(it) == Author.key(name) }) }
    }

    private var scanJob: kotlinx.coroutines.Job? = null
    /**
     * Feeds don't say who wrote a story, so while anyone is blocked, read the bylines of the articles in loaded lists
     * (a few at a time, disk-cached) and hide matches as they are found.
     */
    private fun scanBylines() {
        if (blockedAuthors.value.isEmpty()) return
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            val known = _bylines.value.keys
            val urls = (_home.value.breaking + _home.value.mostRead + _top.value + _byCat.value.values.flatten() +
                _pages.value.values.filterNotNull().flatten() + _searchHits.value.second)
                .map { it.url }.distinct().filter { it !in known && DeepLinks.parse(it) is DeepLinks.Link.Article }
            repo.bylines(urls) { url, a -> _bylines.update { it + (url to a) } }
        }
    }

    val alertEnabled = prefs.alertEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val alertOnlyFollows = prefs.alertOnlyFollows.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val alertMode = prefs.alertMode.stateIn(viewModelScope, SharingStarted.Eagerly, "breaking")
    val alertInterval = prefs.alertInterval.stateIn(viewModelScope, SharingStarted.Eagerly, 30)
    fun setAlertEnabled(v: Boolean) { viewModelScope.launch { prefs.setAlertEnabled(v); Alerts.schedule(getApplication(), v, alertInterval.value) } }
    fun setAlertOnlyFollows(v: Boolean) { viewModelScope.launch { prefs.setAlertOnlyFollows(v) } }
    fun setAlertMode(v: String) { viewModelScope.launch { prefs.setAlertMode(v) } }
    fun setAlertInterval(v: Int) { viewModelScope.launch { prefs.setAlertInterval(v); Alerts.schedule(getApplication(), alertEnabled.value, v) } }
    fun sendTestAlert() = Alerts.test(getApplication())

    @kotlinx.serialization.Serializable
    private data class SettingsExport(
        val version: Int = 1, val follows: List<String> = emptyList(), val accent: String = "dynamic", val theme: String = "system",
        val alertOnlyFollows: Boolean = false, val alertMode: String = "breaking", val alertInterval: Int = 30,
        val blockedAuthors: List<String> = emptyList(),
    )

    fun exportJson(): String = kotlinx.serialization.json.Json { prettyPrint = true }
        .encodeToString(SettingsExport.serializer(), SettingsExport(1, categories.value, accent.value, themeMode.value, alertOnlyFollows.value, alertMode.value, alertInterval.value, blockedAuthors.value))

    /** Validates then applies an exported settings file. Returns false if it isn't one. */
    fun importJson(text: String): Boolean = runCatching {
        val e = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString(SettingsExport.serializer(), text)
        val follows = e.follows.map(Categories::normalize).filter(Categories::isValid).distinct()
        val accent = if (e.accent == "dynamic" || Regex("#[0-9A-Fa-f]{6}").matches(e.accent)) e.accent else "dynamic"
        val theme = if (e.theme in setOf("system", "light", "dark")) e.theme else "system"
        viewModelScope.launch {
            prefs.set(follows); prefs.setAccent(accent); prefs.setTheme(theme)
            prefs.setAlertOnlyFollows(e.alertOnlyFollows)
            prefs.setAlertMode(if (e.alertMode in setOf("breaking", "top")) e.alertMode else "breaking")
            prefs.setAlertInterval(e.alertInterval.coerceIn(15, 720))
            prefs.setBlockedAuthors(e.blockedAuthors.map(String::trim).filter { it.length in 2..80 }.distinctBy { Author.key(it) })
        }
        true
    }.getOrDefault(false)

    private val _home = MutableStateFlow(HomeData(emptyList(), emptyList()))
    val home: StateFlow<HomeData> = combine(_home, hidden) { h, hid -> HomeData(h.breaking.visible(hid), h.mostRead.visible(hid)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeData(emptyList(), emptyList()))
    private val _byCat = MutableStateFlow<Map<String, List<Story>>>(emptyMap())
    private val _top = MutableStateFlow<List<Story>>(emptyList())
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()
    private val _blocks = MutableStateFlow<Map<String, List<Block>>>(emptyMap())
    val blocks = _blocks.asStateFlow()
    private val _articleTopics = MutableStateFlow<Map<String, List<TopicRef>>>(emptyMap())
    val articleTopics = _articleTopics.asStateFlow()

    private val _articleInfo = MutableStateFlow<Map<String, ArticleInfo>>(emptyMap())
    /** Byline and dates per article URL. */
    val articleInfo = _articleInfo.asStateFlow()

    private val _pages = MutableStateFlow<Map<String, List<Story>?>>(emptyMap())
    /** Author/topic pages by follow key ("author:Name", "topic:id|Name"); a null value means still loading. */
    val pages: StateFlow<Map<String, List<Story>?>> by lazy {
        combine(_pages, hidden) { m, h -> m.mapValues { (_, l) -> l?.visible(h) } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    }
    fun loadPage(key: String, force: Boolean = false) {
        if (!force && _pages.value[key] != null) return
        if (_pages.value.containsKey(key) && _pages.value[key] == null) return // already loading
        _pages.update { it + (key to null) }
        viewModelScope.launch {
            val list = runCatching { repo.followStories(key) }.getOrDefault(emptyList())
            _pages.update { it + (key to list) }
            scanBylines()
        }
    }

    private val _searchHits = MutableStateFlow("" to emptyList<Story>())
    /** Live BBC search results for the Search tab, tagged with the query they answer. */
    val searchHits: StateFlow<Pair<String, List<Story>>> by lazy {
        combine(_searchHits, hidden) { (q, l), h -> q to l.visible(h) }.stateIn(viewModelScope, SharingStarted.Eagerly, "" to emptyList())
    }
    private var searchJob: kotlinx.coroutines.Job? = null
    fun search(q: String) {
        searchJob?.cancel()
        val t = q.trim()
        if (t.length < 2) { _searchHits.value = "" to emptyList(); return }
        searchJob = viewModelScope.launch { kotlinx.coroutines.delay(400); _searchHits.value = t to repo.search(t); scanBylines() }
    }

    private val triedKeywords = mutableSetOf<String>()
    /** Turns keyword follows into real BBC topic follows where a matching topic exists (once per keyword per launch). */
    private fun resolveKeywords() = viewModelScope.launch {
        for (c in categories.value.filter { Categories.isKeyword(it) && triedKeywords.add(it) }) {
            val t = runCatching { repo.resolveTopic(c.removePrefix(Categories.KW)) }
                .onFailure { triedKeywords.remove(c) } // retry on the next refresh/launch
                .getOrNull() ?: continue
            val f = Categories.topicFollow(t.id, t.name)
            val cur = categories.value
            if (c in cur) prefs.set(cur.map { if (it == c) f else it }.distinct())
        }
    }

    /** Opens a tapped bbc.com / bbc.co.uk news link in the reader (title/image are read from the page first). */
    fun openLink(link: DeepLinks.Link) { viewModelScope.launch { pendingOpen.value = repo.storyFromLink(link) } }

    // In-app updates (GitHub Releases of this repo).
    sealed interface UpdateState {
        data object Idle : UpdateState
        data object Checking : UpdateState
        data object UpToDate : UpdateState
        data class Available(val release: UpdateChecker.Release) : UpdateState
        data class Downloading(val progress: Float) : UpdateState
        data object Installing : UpdateState
        data class Error(val message: String) : UpdateState
    }
    private val http = okhttp3.OkHttpClient()
    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update = _update.asStateFlow()
    val installResult = Installer.result.asStateFlow()
    private val installedCode: Long get() = getApplication<Application>().packageManager.getPackageInfo(getApplication<Application>().packageName, 0).longVersionCode
    val installedName: String get() = getApplication<Application>().packageManager.getPackageInfo(getApplication<Application>().packageName, 0).versionName.orEmpty()

    fun checkForUpdate() {
        if (_update.value is UpdateState.Checking || _update.value is UpdateState.Downloading) return
        _update.value = UpdateState.Checking
        viewModelScope.launch {
            val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { UpdateChecker.check(http, installedCode) }
            prefs.setUpdateCheckedAt(System.currentTimeMillis())
            _update.value = when (r) {
                is UpdateChecker.Result.Found -> UpdateState.Available(r.release)
                UpdateChecker.Result.UpToDate -> UpdateState.UpToDate
                is UpdateChecker.Result.Failed -> UpdateState.Error(r.message)
            }
        }
    }

    fun installUpdate(r: UpdateChecker.Release) {
        val ctx = getApplication<Application>()
        if (!Installer.canInstall(ctx)) {
            _update.value = UpdateState.Error("Allow Firsthand to install apps, then tap Install again.")
            Installer.openInstallPermission(ctx); return
        }
        Installer.result.value = null
        _update.value = UpdateState.Downloading(0f)
        viewModelScope.launch {
            runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val apk = Installer.download(ctx, http, r) { p -> _update.value = UpdateState.Downloading(p) }
                    Installer.install(ctx, apk)
                }
            }.onSuccess { _update.value = UpdateState.Installing }
                .onFailure { _update.value = UpdateState.Error(it.message ?: "Update failed.") }
        }
    }

    /** A story to open on screen, set when the app is launched from an alert or a BBC link. */
    val pendingOpen = MutableStateFlow<Story?>(null)

    fun toggleFollow(f: String) { val c = categories.value; setCategories(if (f in c) c - f else c + f) }

    /** Stories per followed item, in the user's order. Keywords match across everything loaded. */
    val topicStories: StateFlow<Map<String, List<Story>>> = combine(categories, _byCat, _home, _top, hidden) { cats, by, h, top, hid ->
        val pool = (h.breaking + h.mostRead + top + by.values.flatten()).distinctBy { it.url }
        cats.associateWith { c ->
            if (Categories.isKeyword(c)) {
                // BBC search results, plus anything already loaded that mentions the word.
                val k = c.removePrefix(Categories.KW).lowercase()
                (by[c].orEmpty() + pool.filter { (it.title + " " + it.summary).lowercase().contains(k) }).distinctBy { it.url }.visible(hid)
            } else by[c].orEmpty().visible(hid)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** "For you": round-robin mix across followed items, in the user's order. */
    val forYou: StateFlow<List<Story>> = topicStories.map { m ->
        val lists = m.values.toList()
        buildList { for (i in 0 until 6) lists.forEach { l -> l.getOrNull(i)?.let(::add) } }.distinctBy { it.url }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val searchPool: StateFlow<List<Story>> = combine(_home, _byCat, _top, hidden) { h, by, top, hid ->
        (h.breaking + h.mostRead + top + by.values.flatten()).distinctBy { it.url }.visible(hid)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        Alerts.ensureChannel(app)
        viewModelScope.launch { Alerts.schedule(app, prefs.alertEnabled.first(), prefs.alertInterval.first()) }
        // Load feeds for followed topics as the list changes (only what's missing).
        viewModelScope.launch {
            categories.collect { cats ->
                val missing = cats.filter { it !in _byCat.value }
                if (missing.isNotEmpty()) { _byCat.update { it + repo.allCategories(missing) }; scanBylines() }
                resolveKeywords()
            }
        }
        refresh()
        viewModelScope.launch { blockedAuthors.collect { scanBylines() } }
        // Quiet update check at most every 12 hours; the result shows in Settings > Updates.
        viewModelScope.launch {
            if (System.currentTimeMillis() - prefs.updateCheckedAt.first() > 12 * 3600_000L) checkForUpdate()
        }
    }

    fun refresh() = viewModelScope.launch {
        _refreshing.value = true
        _home.value = repo.home()
        _top.value = repo.top()
        _byCat.value = repo.allCategories(categories.value)
        _refreshing.value = false
        scanBylines()
    }

    /** Video page details by URL; a null value means the page couldn't be read. */
    private val _videos = MutableStateFlow<Map<String, VideoInfo?>>(emptyMap())
    val videos = _videos.asStateFlow()
    fun loadVideo(s: Story) {
        if (_videos.value.containsKey(s.url)) return
        viewModelScope.launch { val v = repo.video(s); _videos.update { it + (s.url to v) } }
    }
    suspend fun videoStream(versionId: String): String? = repo.videoStream(versionId)

    /** Where a share.google / search.app link really goes. */
    suspend fun resolveShortLink(url: String): String = repo.resolveShortLink(url)

    private val _live = MutableStateFlow<Map<String, List<LivePost>>>(emptyMap())
    val live = _live.asStateFlow()

    fun loadLive(s: Story) { viewModelScope.launch { _live.update { it + (s.url to repo.live(s)) } } }

    fun setCategories(list: List<String>) { viewModelScope.launch { prefs.set(list) } }

    fun loadArticle(s: Story) {
        if (_blocks.value.containsKey(s.url)) return
        viewModelScope.launch {
            val a = repo.article(s)
            _blocks.update { it + (s.url to a.blocks) }
            _articleTopics.update { it + (s.url to a.topics) }
            _articleInfo.update { it + (s.url to a.info) }
            if (a.info.authors.isNotEmpty()) _bylines.update { it + (s.url to a.info.authors) }
        }
    }
}
