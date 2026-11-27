package app.firsthand

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.firsthand.data.*
import app.firsthand.notify.Alerts
import app.firsthand.platform.SourceLinks
import app.firsthand.update.Installer
import app.firsthand.update.UpdateChecker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    private val prefs = Prefs(app)

    /** BBC News on/off. Off hides everything from the BBC (Home headlines, topics, writers, search, alerts); the choices are kept. */
    val readAnySite = prefs.readAnySite.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    fun setReadAnySite(v: Boolean) { viewModelScope.launch { prefs.setReadAnySite(v) } }
    val bbcEnabled = prefs.bbcEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    fun setBbcEnabled(v: Boolean) {
        viewModelScope.launch {
            prefs.setBbcEnabled(v)
            if (!v) {
                _home.value = HomeData(emptyList(), emptyList()); _top.value = emptyList(); _searchHits.value = "" to emptyList()
                _byCat.update { it.filterKeys { k -> !Categories.isBbc(k) } } // no BBC stories left in search or For you
            }
            // The preference flows update a moment later, so work from the value just chosen.
            refreshWith(v, follows.value.let { f -> if (v) f else f.filterNot(Categories::isBbc) })
        }
    }

    /** Everything followed, including BBC follows hidden while BBC is off. All writes go through this so nothing is lost. */
    private val follows = prefs.categories.stateIn(viewModelScope, SharingStarted.Eagerly, listOf("World", "Tech", "Sport", "Science"))
    /** What is shown and loaded: the follows, minus BBC's while BBC is off. */
    val categories: StateFlow<List<String>> = combine(follows, bbcEnabled) { f, on -> if (on) f else f.filterNot(Categories::isBbc) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, listOf("World", "Tech", "Sport", "Science"))
    val accent = prefs.accent.stateIn(viewModelScope, SharingStarted.Eagerly, "dynamic")
    val themeMode = prefs.theme.stateIn(viewModelScope, SharingStarted.Eagerly, "system")
    fun setAccent(v: String) { viewModelScope.launch { prefs.setAccent(v) } }
    fun setThemeMode(v: String) { viewModelScope.launch { prefs.setTheme(v) } }
    private val barOrderStored = prefs.barOrder.stateIn(viewModelScope, SharingStarted.Eagerly, BarOrder.default)
    /** The bottom bar's buttons in the user's order (Breaking left out without BBC News). */
    val barOrder: StateFlow<List<String>> = combine(barOrderStored, bbcEnabled) { o, b -> BarOrder.visible(o, b) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, BarOrder.default)
    fun setBarOrder(visible: List<String>) { viewModelScope.launch { prefs.setBarOrder(BarOrder.merge(visible, barOrderStored.value)) } }
    fun resetBarOrder() { viewModelScope.launch { prefs.setBarOrder(BarOrder.default) } }
    /** Starts true (hidden) so the tip doesn't flash before the saved value loads. */
    val barTipSeen = prefs.barTipSeen.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    fun dismissBarTip() { viewModelScope.launch { prefs.setBarTipSeen(true) } }

    /** Stored source entries and the publishers they stand for. BBC News is built in; these are the ones added in Settings. */
    private val sourceEntries = prefs.sources.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val sources: StateFlow<List<Source>> = sourceEntries.map(Sources::resolveAll).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Adds a catalogue publisher and follows its front page so it shows up in My News straight away. */
    fun addSource(s: Source) {
        viewModelScope.launch {
            prefs.setSources((sourceEntries.value + Sources.presetEntry(s)).distinct())
            val f = s.follow(s.frontPage)
            if (f !in follows.value) prefs.set(follows.value + f)
        }
    }

    /** Adds a feed by address after checking it really is a feed. [done] gets a message to show the user. */
    fun addCustomSource(url: String, name: String, done: (String) -> Unit) {
        val u = url.trim()
        if (!Sources.validUrl(u)) { done("Enter a full https:// address for the feed."); return }
        if (sources.value.any { s -> s.feeds.any { it.url == u } }) { done("That feed is already added."); return }
        viewModelScope.launch {
            val feed = runCatching { repo.checkFeed(u) }.getOrNull()
            if (feed == null) { done("Couldn't read that address as an RSS or Atom feed."); return@launch }
            if (feed.stories.isEmpty()) { done("That feed has no articles in it."); return@launch }
            val n = name.ifBlank { feed.title ?: java.net.URI(u).host }.replace(Regex("\\s+"), " ").trim().take(80)
            val src = Sources.resolve(Sources.customEntry(u, n))
            if (src == null) { done("Couldn't add that feed."); return@launch }
            prefs.setSources((sourceEntries.value + Sources.customEntry(u, n)).distinct())
            val f = src.follow(src.frontPage)
            if (f !in follows.value) prefs.set(follows.value + f)
            done("Added ${src.name}.")
        }
    }

    /** Removes a source and stops following any of its sections. */
    fun removeSource(s: Source) {
        viewModelScope.launch {
            prefs.setSources(sourceEntries.value.filter { e -> Sources.resolve(e)?.id != s.id })
            prefs.set(follows.value.filterNot { s.owns(it) })
        }
    }

    /** Writers the user has blocked (display names). Their articles, and any they co-wrote, are hidden everywhere. */
    val blockedAuthors = prefs.blockedAuthors.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _bylines = MutableStateFlow<Map<String, List<Author>>>(emptyMap())
    /** URLs of articles credited (even partly) to a blocked writer. */
    /** Keywords the user has blocked, as stored lines ("subject|Justin Bieber"), and the parsed rules. */
    val blockedTerms = prefs.blockedTerms.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val blockedRules: StateFlow<List<BlockRule>> = blockedTerms.map(Blocking::parseAll).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Everything to hide: articles by blocked writers (by URL) and stories matching a blocked keyword. */
    private class Hide(val urls: Set<String>, val rules: List<BlockRule>) {
        val isEmpty get() = urls.isEmpty() && rules.isEmpty()
        fun hides(s: Story) = s.url in urls || rules.any { it.matches(s) }
    }
    private val hidden: StateFlow<Hide> = combine(blockedAuthors, _bylines, blockedRules) { b, by, rules ->
        val keys = b.map { Author.key(it) }.toSet()
        Hide(if (keys.isEmpty()) emptySet() else by.filterValues { a -> a.any { Author.key(it.name) in keys } }.keys, rules)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Hide(emptySet(), emptyList()))
    private fun List<Story>.visible(h: Hide) = if (h.isEmpty) this else filterNot { h.hides(it) }

    /** Adds a blocked keyword. Returns a message if it can't be added, else null. */
    fun addBlockedTerm(raw: String, scope: BlockScope): String? {
        val term = Blocking.clean(raw) ?: return "Enter 2 to 60 characters, with at least one letter or number, and no | symbol."
        val rule = BlockRule(term, scope)
        if (rule in blockedRules.value) return "You've already blocked that."
        viewModelScope.launch { prefs.setBlockedTerms(blockedTerms.value + rule.line) }
        return null
    }
    fun removeBlockedTerm(line: String) {
        val gone = Blocking.parse(line)
        viewModelScope.launch { prefs.setBlockedTerms(blockedTerms.value.filterNot { Blocking.parse(it) == gone }) }
    }

    fun isBlocked(name: String) = blockedAuthors.value.any { Author.key(it) == Author.key(name) }
    fun blockAuthor(name: String) {
        viewModelScope.launch {
            prefs.setBlockedAuthors((blockedAuthors.value + name.trim()).distinctBy { Author.key(it) })
            val drop = setOf(Categories.authorFollow(name), Categories.xauthorFollow(name))
            if (follows.value.any { it in drop }) prefs.set(follows.value - drop) // blocking someone also unfollows them
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

    // What to alert on: breaking (per source), shared categories across all sources, keywords, quiet hours.
    val alertBreaking = prefs.alertBreaking.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val alertOffSources = prefs.alertOffSources.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val alertCategories = prefs.alertCategories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val alertKeywords = prefs.alertKeywords.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val quietEnabled = prefs.quietEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val quietStart = prefs.quietStart.stateIn(viewModelScope, SharingStarted.Eagerly, 22)
    val quietEnd = prefs.quietEnd.stateIn(viewModelScope, SharingStarted.Eagerly, 7)
    fun setAlertBreaking(v: Boolean) { viewModelScope.launch { prefs.setAlertBreaking(v) } }
    fun toggleAlertSource(id: String) {
        viewModelScope.launch { prefs.setAlertOffSources(alertOffSources.value.let { if (id in it) it - id else it + id }) }
    }
    fun toggleAlertCategory(cat: String) {
        if (cat !in Categories.sharedCategories) return
        viewModelScope.launch { prefs.setAlertCategories(alertCategories.value.let { if (cat in it) it - cat else it + cat }) }
    }
    /** Adds a keyword alert (same matching as blocked keywords). Returns a message if it can't be added, else null. */
    fun addAlertKeyword(raw: String, scope: BlockScope): String? {
        val term = Blocking.clean(raw) ?: return "Enter 2 to 60 characters, with at least one letter or number, and no | symbol."
        val rule = BlockRule(term, scope)
        if (rule in Blocking.parseAll(alertKeywords.value)) return "You already have that alert."
        viewModelScope.launch { prefs.setAlertKeywords(alertKeywords.value + rule.line) }
        return null
    }
    fun removeAlertKeyword(line: String) {
        val gone = Blocking.parse(line)
        viewModelScope.launch { prefs.setAlertKeywords(alertKeywords.value.filterNot { Blocking.parse(it) == gone }) }
    }
    fun setQuiet(enabled: Boolean, start: Int = quietStart.value, end: Int = quietEnd.value) { viewModelScope.launch { prefs.setQuiet(enabled, start, end) } }

    @kotlinx.serialization.Serializable
    private data class SettingsExport(
        val version: Int = 1, val follows: List<String> = emptyList(), val accent: String = "dynamic", val theme: String = "system",
        val alertOnlyFollows: Boolean = false, val alertMode: String = "breaking", val alertInterval: Int = 30,
        val blockedAuthors: List<String> = emptyList(), val sources: List<String> = emptyList(), val bbc: Boolean = true,
        val blockedTerms: List<String> = emptyList(),
        val alertBreaking: Boolean = true, val alertOffSources: List<String> = emptyList(), val alertCategories: List<String> = emptyList(),
        val alertKeywords: List<String> = emptyList(), val quietEnabled: Boolean = false, val quietStart: Int = 22, val quietEnd: Int = 7,
        val barOrder: List<String> = emptyList(), val readAnySite: Boolean = true,
    )

    fun exportJson(): String = kotlinx.serialization.json.Json { prettyPrint = true }
        .encodeToString(SettingsExport.serializer(), SettingsExport(1, follows.value, accent.value, themeMode.value, alertOnlyFollows.value, alertMode.value, alertInterval.value, blockedAuthors.value, sourceEntries.value, bbcEnabled.value, blockedTerms.value,
            alertBreaking.value, alertOffSources.value, alertCategories.value, alertKeywords.value, quietEnabled.value, quietStart.value, quietEnd.value, barOrderStored.value, readAnySite.value))

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
            prefs.setBbcEnabled(e.bbc)
            prefs.setAlertBreaking(e.alertBreaking)
            prefs.setAlertOffSources(e.alertOffSources.filter { it.length in 1..60 && '\n' !in it }.distinct())
            prefs.setAlertCategories(e.alertCategories.filter { it in Categories.sharedCategories }.distinct())
            prefs.setAlertKeywords(Blocking.parseAll(e.alertKeywords).map { it.line })
            prefs.setQuiet(e.quietEnabled, e.quietStart, e.quietEnd)
            prefs.setReadAnySite(e.readAnySite)
            if (e.barOrder.isNotEmpty()) prefs.setBarOrder(BarOrder.normalise(e.barOrder))
            prefs.setBlockedTerms(Blocking.parseAll(e.blockedTerms).map { it.line })
            prefs.setSources(e.sources.filter { Sources.resolve(it) != null }.distinct())
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
        combine(_pages, hidden, _byCat) { m, h, by ->
            m.mapValues { (k, l) -> (if (Categories.isXAuthor(k)) writerStories(Categories.xauthorName(k), by.values.flatten()) else l)?.visible(h) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    }

    /** A non-BBC writer's stories: whatever their publisher's feeds you follow have loaded, newest first. */
    private fun writerStories(name: String, pool: List<Story>): List<Story> {
        val want = Author.key(name)
        return pool.filter { s -> s.authors.any { Author.key(it.name) == want } }.distinctBy { it.url }.sortedByDescending { it.publishedMillis }
    }

    /** Remembers who wrote each story the feeds named a writer for, so blocking works on them like on BBC bylines. */
    private fun seedBylines(m: Map<String, List<Story>>) {
        val found = m.values.flatten().filter { it.authors.isNotEmpty() }.associate { it.url to it.authors }
        if (found.isNotEmpty()) _bylines.update { it + found }
    }

    fun loadPage(key: String, force: Boolean = false) {
        if (Categories.isXAuthor(key)) { _pages.update { it + (key to emptyList()) }; return } // derived in [pages], nothing to fetch
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
        searchJob = viewModelScope.launch { kotlinx.coroutines.delay(400); _searchHits.value = t to (if (bbcEnabled.value) repo.search(t) else emptyList()); scanBylines() }
    }

    private val triedKeywords = mutableSetOf<String>()
    /** Turns keyword follows into real BBC topic follows where a matching topic exists (once per keyword per launch). */
    private fun resolveKeywords() = viewModelScope.launch {
        if (!bbcEnabled.value) return@launch
        for (c in categories.value.filter { Categories.isKeyword(it) && triedKeywords.add(it) }) {
            val t = runCatching { repo.resolveTopic(c.removePrefix(Categories.KW)) }
                .onFailure { triedKeywords.remove(c) } // retry on the next refresh/launch
                .getOrNull() ?: continue
            val f = Categories.topicFollow(t.id, t.name)
            val cur = follows.value
            if (c in cur) prefs.set(cur.map { if (it == c) f else it }.distinct())
        }
    }

    /** The added source a link belongs to (catalogue sources open from other apps; custom feeds from Share > Firsthand), or null. */
    fun linkSource(url: String): Source? = Sources.matchLink(url, sources.value)

    /** Opens an added source's article in the reader (headline and picture are read from the page first). */
    /** Opens any shared web page in the reader (headline and picture from the page; the site's address gives its name). */
    fun openWebLink(url: String) { viewModelScope.launch { pendingOpen.value = repo.storyFromSourceLink(url, Sources.siteName(url), pageName = true) } }

    fun openSourceLink(src: Source, url: String) { viewModelScope.launch { pendingOpen.value = repo.storyFromSourceLink(url, src.name) } }

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
            if (Categories.isXAuthor(c)) writerStories(Categories.xauthorName(c), by.values.flatten()).visible(hid)
            else if (Categories.isKeyword(c)) {
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
        // Which publishers' links the app is eligible to open follows what is added (and BBC), so Android's "Open by default" lists them.
        viewModelScope.launch { combine(sources, bbcEnabled) { s, b -> s to b }.collect { (s, b) -> SourceLinks.sync(app, s, b) } }
        viewModelScope.launch { Alerts.schedule(app, prefs.alertEnabled.first(), prefs.alertInterval.first()) }
        // Load feeds for followed topics as the list changes (only what's missing).
        viewModelScope.launch {
            categories.collect { cats ->
                val missing = cats.filter { it !in _byCat.value }
                if (missing.isNotEmpty()) { val got = repo.allCategories(missing); _byCat.update { it + got }; seedBylines(got); scanBylines() }
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

    fun refresh() = refreshWith(bbcEnabled.value, categories.value)

    private fun refreshWith(bbc: Boolean, cats: List<String>) = viewModelScope.launch {
        _refreshing.value = true
        if (bbc) { _home.value = repo.home(); _top.value = repo.top() }
        _byCat.value = repo.allCategories(cats).also(::seedBylines)
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

    /** Saves the visible follows, keeping any BBC ones that are hidden while BBC is off. */
    fun setCategories(list: List<String>) {
        viewModelScope.launch { prefs.set(list + if (bbcEnabled.value) emptyList() else follows.value.filter(Categories::isBbc).filterNot { it in list }) }
    }

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
