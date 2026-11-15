package app.bbcrevived

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.bbcrevived.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    private val prefs = Prefs(app)

    val categories = prefs.categories.stateIn(viewModelScope, SharingStarted.Eagerly, listOf("World", "Tech", "Sport", "Science"))
    private val _home = MutableStateFlow(HomeData(emptyList(), emptyList()))
    val home = _home.asStateFlow()
    private val _byCat = MutableStateFlow<Map<String, List<Story>>>(emptyMap())
    private val _top = MutableStateFlow<List<Story>>(emptyList())
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()
    private val _blocks = MutableStateFlow<Map<String, List<Block>>>(emptyMap())
    val blocks = _blocks.asStateFlow()

    /** "For you": round-robin mix across chosen categories, in the user's order. */
    val forYou: StateFlow<List<Story>> = combine(categories, _byCat, _home, _top) { cats, by, h, top ->
        val pool = (h.breaking + h.mostRead + top + by.values.flatten()).distinctBy { it.url }
        val lists = cats.map { c ->
            if (Categories.isKeyword(c)) {
                val k = c.removePrefix(Categories.KW).lowercase()
                pool.filter { (it.title + " " + it.summary).lowercase().contains(k) }
            } else by[c].orEmpty()
        }
        buildList { for (i in 0 until 6) lists.forEach { l -> l.getOrNull(i)?.let(::add) } }.distinctBy { it.url }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val searchPool: StateFlow<List<Story>> = combine(_home, _byCat, _top) { h, by, top ->
        (h.breaking + h.mostRead + top + by.values.flatten()).distinctBy { it.url }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // Load feeds for followed topics as the list changes (only what's missing).
        viewModelScope.launch {
            categories.collect { cats ->
                val missing = cats.filter { !Categories.isKeyword(it) && it !in _byCat.value }
                if (missing.isNotEmpty()) _byCat.update { it + repo.allCategories(missing) }
            }
        }
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _refreshing.value = true
        _home.value = repo.home()
        _top.value = repo.top()
        _byCat.value = repo.allCategories(categories.value.filter { !Categories.isKeyword(it) })
        _refreshing.value = false
    }

    private val _live = MutableStateFlow<Map<String, List<LivePost>>>(emptyMap())
    val live = _live.asStateFlow()

    fun loadLive(s: Story) { viewModelScope.launch { _live.update { it + (s.url to repo.live(s)) } } }

    fun setCategories(list: List<String>) { viewModelScope.launch { prefs.set(list) } }

    fun loadArticle(s: Story) {
        if (_blocks.value.containsKey(s.url)) return
        viewModelScope.launch { _blocks.update { it + (s.url to repo.article(s)) } }
    }
}
