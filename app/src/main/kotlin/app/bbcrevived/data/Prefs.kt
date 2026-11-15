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
        p[key]?.split('|')?.filter { it in Categories.feeds } ?: default
    }

    suspend fun set(list: List<String>) { ctx.store.edit { it[key] = list.joinToString("|") } }
}
