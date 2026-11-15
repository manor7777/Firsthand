package app.bbcrevived

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.bbcrevived.data.*
import app.bbcrevived.notify.Alerts
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
    )

    fun exportJson(): String = kotlinx.serialization.json.Json { prettyPrint = true }
        .encodeToString(SettingsExport.serializer(), SettingsExport(1, categories.value, accent.value, themeMode.value, alertOnlyFollows.value, alertMode.value, alertInterval.value))

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
        }
        true
    }.getOrDefault(false)

    private val _home = MutableStateFlow(HomeData(emptyList(), emptyList()))
    val home = _home.asStateFlow()
    private val _byCat = MutableStateFlow<Map<String, List<Story>>>(emptyMap())
    private val _top = MutableStateFlow<List<Story>>(emptyList())
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()
    private val _blocks = MutableStateFlow<Map<String, List<Block>>>(emptyMap())
    val blocks = _blocks.asStateFlow()
    private val _articleTopics = MutableStateFlow<Map<String, List<TopicRef>>>(emptyMap())
    val articleTopics = _articleTopics.asStateFlow()

    /** A story to open on screen, set when the app is launched from an alert. */
    val pendingOpen = MutableStateFlow<Story?>(null)

    fun toggleFollow(f: String) { val c = categories.value; setCategories(if (f in c) c - f else c + f) }

    /** Stories per followed item, in the user's order. Keywords match across everything loaded. */
    val topicStories: StateFlow<Map<String, List<Story>>> = combine(categories, _byCat, _home, _top) { cats, by, h, top ->
        val pool = (h.breaking + h.mostRead + top + by.values.flatten()).distinctBy { it.url }
        cats.associateWith { c ->
            if (Categories.isKeyword(c)) {
                // BBC search results, plus anything already loaded that mentions the word.
                val k = c.removePrefix(Categories.KW).lowercase()
                (by[c].orEmpty() + pool.filter { (it.title + " " + it.summary).lowercase().contains(k) }).distinctBy { it.url }
            } else by[c].orEmpty()
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** "For you": round-robin mix across followed items, in the user's order. */
    val forYou: StateFlow<List<Story>> = topicStories.map { m ->
        val lists = m.values.toList()
        buildList { for (i in 0 until 6) lists.forEach { l -> l.getOrNull(i)?.let(::add) } }.distinctBy { it.url }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val searchPool: StateFlow<List<Story>> = combine(_home, _byCat, _top) { h, by, top ->
        (h.breaking + h.mostRead + top + by.values.flatten()).distinctBy { it.url }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        Alerts.ensureChannel(app)
        viewModelScope.launch { Alerts.schedule(app, prefs.alertEnabled.first(), prefs.alertInterval.first()) }
        // Load feeds for followed topics as the list changes (only what's missing).
        viewModelScope.launch {
            categories.collect { cats ->
                val missing = cats.filter { it !in _byCat.value }
                if (missing.isNotEmpty()) _byCat.update { it + repo.allCategories(missing) }
            }
        }
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _refreshing.value = true
        _home.value = repo.home()
        _top.value = repo.top()
        _byCat.value = repo.allCategories(categories.value)
        _refreshing.value = false
    }

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
        }
    }
}
