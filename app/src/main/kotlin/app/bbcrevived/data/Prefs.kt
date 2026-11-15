package app.bbcrevived.data

import android.content.Context
import androidx.datastore.preferences.core.edit
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
        p[key]?.split('|')?.filter { it in Categories.feeds || Categories.isKeyword(it) } ?: default
    }

    private val accentKey = stringPreferencesKey("accent")
    private val themeKey = stringPreferencesKey("theme")
    val accent: Flow<String> = ctx.store.data.map { it[accentKey] ?: "dynamic" }
    val theme: Flow<String> = ctx.store.data.map { it[themeKey] ?: "system" }
    suspend fun setAccent(v: String) { ctx.store.edit { it[accentKey] = v } }
    suspend fun setTheme(v: String) { ctx.store.edit { it[themeKey] = v } }

    suspend fun set(list: List<String>) { ctx.store.edit { it[key] = list.joinToString("|") } }
}
