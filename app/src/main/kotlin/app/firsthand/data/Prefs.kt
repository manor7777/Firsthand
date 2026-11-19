package app.firsthand.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("prefs")

/** Ordered list of chosen categories ("My News"). */
class Prefs(private val ctx: Context) {
    private val key = stringPreferencesKey("categories")
    private val default = listOf("World", "Tech", "Sport", "Science")

    val categories: Flow<List<String>> = ctx.store.data.map { p ->
        p[key]?.let { v -> (if ('\n' in v) v.split('\n') else v.split('|')) }?.map(Categories::normalize)?.filter(Categories::isValid)?.distinct() ?: default
    }

    private val accentKey = stringPreferencesKey("accent")
    private val themeKey = stringPreferencesKey("theme")
    val accent: Flow<String> = ctx.store.data.map { it[accentKey] ?: "dynamic" }
    val theme: Flow<String> = ctx.store.data.map { it[themeKey] ?: "system" }
    suspend fun setAccent(v: String) { ctx.store.edit { it[accentKey] = v } }
    suspend fun setTheme(v: String) { ctx.store.edit { it[themeKey] = v } }
    private val barKey = stringPreferencesKey("bar_order")
    /** The bottom bar's button order (see BarOrder); always all four ids. */
    val barOrder: Flow<List<String>> = ctx.store.data.map { BarOrder.parse(it[barKey]) }
    suspend fun setBarOrder(v: List<String>) { ctx.store.edit { it[barKey] = BarOrder.encode(v) } }
    private val barTipKey = booleanPreferencesKey("bar_tip_seen")
    /** True once the one-time "press and hold to rearrange" tip has been dismissed. */
    val barTipSeen: Flow<Boolean> = ctx.store.data.map { it[barTipKey] ?: false }
    suspend fun setBarTipSeen(v: Boolean) { ctx.store.edit { it[barTipKey] = v } }

    // Breaking-news alerts
    private val aEnabled = booleanPreferencesKey("alert_enabled")
    private val aFollows = booleanPreferencesKey("alert_only_follows")
    private val aMode = stringPreferencesKey("alert_mode")
    private val aInterval = intPreferencesKey("alert_interval_min")
    private val aSeen = stringPreferencesKey("alert_seen")
    val alertEnabled: Flow<Boolean> = ctx.store.data.map { it[aEnabled] ?: false }
    val alertOnlyFollows: Flow<Boolean> = ctx.store.data.map { it[aFollows] ?: false }
    val alertMode: Flow<String> = ctx.store.data.map { it[aMode] ?: "breaking" }
    val alertInterval: Flow<Int> = ctx.store.data.map { it[aInterval] ?: 30 }
    val alertSeen: Flow<List<String>> = ctx.store.data.map { it[aSeen]?.split('\n')?.filter(String::isNotEmpty) ?: emptyList() }
    suspend fun setAlertEnabled(v: Boolean) { ctx.store.edit { it[aEnabled] = v } }
    suspend fun setAlertOnlyFollows(v: Boolean) { ctx.store.edit { it[aFollows] = v } }
    suspend fun setAlertMode(v: String) { ctx.store.edit { it[aMode] = v } }
    suspend fun setAlertInterval(v: Int) { ctx.store.edit { it[aInterval] = v } }
    suspend fun setAlertSeen(v: List<String>) { ctx.store.edit { it[aSeen] = v.joinToString("\n") } }

    // What to alert on beyond the basics: breaking on/off, which sources, shared categories, keywords, quiet hours.
    private val aBreaking = booleanPreferencesKey("alert_breaking")
    private val aOffSources = stringPreferencesKey("alert_off_sources")
    private val aCategories = stringPreferencesKey("alert_categories")
    private val aKeywords = stringPreferencesKey("alert_keywords")
    private val aPrimed = stringPreferencesKey("alert_primed")
    private val qEnabled = booleanPreferencesKey("quiet_enabled")
    private val qStart = intPreferencesKey("quiet_start")
    private val qEnd = intPreferencesKey("quiet_end")
    private fun lines(v: String?) = v?.split('\n')?.filter(String::isNotBlank) ?: emptyList()
    val alertBreaking: Flow<Boolean> = ctx.store.data.map { it[aBreaking] ?: true }
    /** Source ids ("bbc" or a catalogue id) switched off for alerts; everything added is on by default. */
    val alertOffSources: Flow<List<String>> = ctx.store.data.map { lines(it[aOffSources]) }
    /** Shared categories (see Categories.sharedCategories) to alert on across every source. */
    val alertCategories: Flow<List<String>> = ctx.store.data.map { lines(it[aCategories]) }
    /** Keyword alert rules, stored like blocked keywords ("subject|term"). */
    val alertKeywords: Flow<List<String>> = ctx.store.data.map { lines(it[aKeywords]) }
    /** Alert rules that have already been learned once (see AlertPlanner). */
    val alertPrimed: Flow<List<String>> = ctx.store.data.map { lines(it[aPrimed]) }
    val quietEnabled: Flow<Boolean> = ctx.store.data.map { it[qEnabled] ?: false }
    val quietStart: Flow<Int> = ctx.store.data.map { it[qStart] ?: 22 }
    val quietEnd: Flow<Int> = ctx.store.data.map { it[qEnd] ?: 7 }
    suspend fun setAlertBreaking(v: Boolean) { ctx.store.edit { it[aBreaking] = v } }
    suspend fun setAlertOffSources(v: List<String>) { ctx.store.edit { it[aOffSources] = v.joinToString("\n") } }
    suspend fun setAlertCategories(v: List<String>) { ctx.store.edit { it[aCategories] = v.joinToString("\n") } }
    suspend fun setAlertKeywords(v: List<String>) { ctx.store.edit { it[aKeywords] = v.joinToString("\n") } }
    suspend fun setAlertPrimed(v: Collection<String>) { ctx.store.edit { it[aPrimed] = v.joinToString("\n") } }
    suspend fun setQuiet(enabled: Boolean, start: Int, end: Int) { ctx.store.edit { it[qEnabled] = enabled; it[qStart] = start.coerceIn(0, 23); it[qEnd] = end.coerceIn(0, 23) } }

    // Writers whose articles are hidden everywhere (display names, one per line).
    private val blockedKey = stringPreferencesKey("blocked_authors")
    val blockedAuthors: Flow<List<String>> = ctx.store.data.map { it[blockedKey]?.split('\n')?.filter(String::isNotBlank) ?: emptyList() }
    suspend fun setBlockedAuthors(v: List<String>) { ctx.store.edit { it[blockedKey] = v.joinToString("\n") } }

    // Keywords whose stories are hidden: "subject|Justin Bieber" or "story|Justin Bieber", one per line (see Blocking).
    private val termsKey = stringPreferencesKey("blocked_terms")
    val blockedTerms: Flow<List<String>> = ctx.store.data.map { it[termsKey]?.split('\n')?.filter(String::isNotBlank) ?: emptyList() }
    suspend fun setBlockedTerms(v: List<String>) { ctx.store.edit { it[termsKey] = v.joinToString("\n") } }

    // BBC News is a source like any other and can be switched off (its follows are kept, just hidden, so switching back restores them).
    private val bbcKey = booleanPreferencesKey("bbc_enabled")
    private val readAnyKey = booleanPreferencesKey("read_any_site")
    /** Whether a website shared to the app opens in the reader (default) or just in the browser. */
    val readAnySite: Flow<Boolean> = ctx.store.data.map { it[readAnyKey] ?: true }
    suspend fun setReadAnySite(v: Boolean) { ctx.store.edit { it[readAnyKey] = v } }
    val bbcEnabled: Flow<Boolean> = ctx.store.data.map { it[bbcKey] ?: true }
    suspend fun setBbcEnabled(v: Boolean) { ctx.store.edit { it[bbcKey] = v } }

    // Added news sources: "p:<catalogue id>" or "c:<https rss url>|<name>", one per line (see Sources).
    private val sourcesKey = stringPreferencesKey("sources")
    val sources: Flow<List<String>> = ctx.store.data.map { it[sourcesKey]?.split('\n')?.filter(String::isNotBlank) ?: emptyList() }
    suspend fun setSources(v: List<String>) { ctx.store.edit { it[sourcesKey] = v.joinToString("\n") } }

    private val updChecked = longPreferencesKey("update_checked_at")
    val updateCheckedAt: Flow<Long> = ctx.store.data.map { it[updChecked] ?: 0L }
    suspend fun setUpdateCheckedAt(v: Long) { ctx.store.edit { it[updChecked] = v } }

    suspend fun set(list: List<String>) { ctx.store.edit { it[key] = list.joinToString("\n") } }
}
