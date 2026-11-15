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
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()
    private val _blocks = MutableStateFlow<Map<String, List<Block>>>(emptyMap())
    val blocks = _blocks.asStateFlow()

    /** "For you": round-robin mix across chosen categories, in the user's order. */
    val forYou: StateFlow<List<Story>> = combine(categories, _byCat) { cats, by ->
        val lists = cats.map { by[it].orEmpty() }
        buildList { for (i in 0 until 6) lists.forEach { l -> l.getOrNull(i)?.let(::add) } }.distinctBy { it.url }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val searchPool: StateFlow<List<Story>> = combine(_home, _byCat) { h, by ->
        (h.breaking + h.mostRead + by.values.flatten()).distinctBy { it.url }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _refreshing.value = true
        _home.value = repo.home()
        _byCat.value = repo.allCategories(Categories.feeds.keys)
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
