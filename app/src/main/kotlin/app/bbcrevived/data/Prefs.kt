package app.bbcrevived.data

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

    // Writers whose articles are hidden everywhere (display names, one per line).
    private val blockedKey = stringPreferencesKey("blocked_authors")
    val blockedAuthors: Flow<List<String>> = ctx.store.data.map { it[blockedKey]?.split('\n')?.filter(String::isNotBlank) ?: emptyList() }
    suspend fun setBlockedAuthors(v: List<String>) { ctx.store.edit { it[blockedKey] = v.joinToString("\n") } }

    private val updChecked = longPreferencesKey("update_checked_at")
    val updateCheckedAt: Flow<Long> = ctx.store.data.map { it[updChecked] ?: 0L }
    suspend fun setUpdateCheckedAt(v: Long) { ctx.store.edit { it[updChecked] = v } }

    suspend fun set(list: List<String>) { ctx.store.edit { it[key] = list.joinToString("\n") } }
}
