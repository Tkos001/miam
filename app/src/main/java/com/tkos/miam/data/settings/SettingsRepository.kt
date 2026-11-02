package com.tkos.miam.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class AppTheme(val label: String) {
    SYSTEM("Suivre le système"),
    LIGHT("Clair"),
    DARK("Sombre"),
    PURPLE_DARK("Mauve sombre"),
    RED_DARK("Rouge sombre");

    companion object {
        fun fromName(name: String?): AppTheme = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

data class Settings(
    val theme: AppTheme = AppTheme.SYSTEM,
    val groupByAisle: Boolean = true,
    val defaultServings: Int = 4,
    val recipesUrl: String = "",
    val seeded: Boolean = false,
    val weekStartsMonday: Boolean = true,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val GROUP_BY_AISLE = booleanPreferencesKey("group_by_aisle")
        val DEFAULT_SERVINGS = intPreferencesKey("default_servings")
        val RECIPES_URL = stringPreferencesKey("recipes_url")
        val SEEDED = booleanPreferencesKey("seeded")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            theme = AppTheme.fromName(p[Keys.THEME]),
            groupByAisle = p[Keys.GROUP_BY_AISLE] ?: true,
            defaultServings = p[Keys.DEFAULT_SERVINGS] ?: 4,
            recipesUrl = p[Keys.RECIPES_URL] ?: "",
            seeded = p[Keys.SEEDED] ?: false,
        )
    }

    suspend fun setTheme(theme: AppTheme) = context.dataStore.edit { it[Keys.THEME] = theme.name }
    suspend fun setGroupByAisle(value: Boolean) = context.dataStore.edit { it[Keys.GROUP_BY_AISLE] = value }
    suspend fun setDefaultServings(value: Int) = context.dataStore.edit { it[Keys.DEFAULT_SERVINGS] = value.coerceIn(1, 20) }
    suspend fun setRecipesUrl(value: String) = context.dataStore.edit { it[Keys.RECIPES_URL] = value.trim() }
    suspend fun setSeeded(value: Boolean) = context.dataStore.edit { it[Keys.SEEDED] = value }
}
