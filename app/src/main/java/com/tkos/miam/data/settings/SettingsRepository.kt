package com.tkos.miam.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
    /** URL du catalogue de recettes distant ; vide = catalogue par défaut du dépôt git. */
    val catalogUrl: String = "",
    /** Dernière version du catalogue intégrée (0 = jamais synchronisé). */
    val catalogVersion: Int = 0,
    /** Horodatage (ms) de la dernière synchronisation réussie, 0 = jamais. */
    val catalogLastSync: Long = 0L,
    /** Synchroniser automatiquement le catalogue au lancement. */
    val catalogAutoSync: Boolean = true,
    /** Token GitHub (fine-grained, Contents read/write) pour publier des recettes dans le catalogue. */
    val githubToken: String = "",
    /** Nom affiché comme auteur des commits de publication (plusieurs personnes peuvent partager un token). */
    val contributorName: String = "",
    /** Ids des recettes modifiées localement et pas encore publiées : la sync ne les écrase pas. */
    val dirtyRecipeIds: Set<Long> = emptySet(),
    /** Assistant IA (optionnel) : fournisseur, clé du compte de l'utilisateur, modèle, URL (Ollama / compatible OpenAI). */
    val aiProvider: String = "NONE",
    val aiApiKey: String = "",
    val aiModel: String = "",
    val aiBaseUrl: String = "",
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val GROUP_BY_AISLE = booleanPreferencesKey("group_by_aisle")
        val DEFAULT_SERVINGS = intPreferencesKey("default_servings")
        val RECIPES_URL = stringPreferencesKey("recipes_url")
        val SEEDED = booleanPreferencesKey("seeded")
        val CATALOG_URL = stringPreferencesKey("catalog_url")
        val CATALOG_VERSION = intPreferencesKey("catalog_version")
        val CATALOG_LAST_SYNC = longPreferencesKey("catalog_last_sync")
        val CATALOG_AUTO_SYNC = booleanPreferencesKey("catalog_auto_sync")
        val GITHUB_TOKEN = stringPreferencesKey("github_token")
        val CONTRIBUTOR_NAME = stringPreferencesKey("contributor_name")
        val DIRTY_RECIPES = stringSetPreferencesKey("dirty_recipes")
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val AI_API_KEY = stringPreferencesKey("ai_api_key")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val AI_BASE_URL = stringPreferencesKey("ai_base_url")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            theme = AppTheme.fromName(p[Keys.THEME]),
            groupByAisle = p[Keys.GROUP_BY_AISLE] ?: true,
            defaultServings = p[Keys.DEFAULT_SERVINGS] ?: 4,
            recipesUrl = p[Keys.RECIPES_URL] ?: "",
            seeded = p[Keys.SEEDED] ?: false,
            catalogUrl = p[Keys.CATALOG_URL] ?: "",
            catalogVersion = p[Keys.CATALOG_VERSION] ?: 0,
            catalogLastSync = p[Keys.CATALOG_LAST_SYNC] ?: 0L,
            catalogAutoSync = p[Keys.CATALOG_AUTO_SYNC] ?: true,
            githubToken = p[Keys.GITHUB_TOKEN] ?: "",
            contributorName = p[Keys.CONTRIBUTOR_NAME] ?: "",
            dirtyRecipeIds = p[Keys.DIRTY_RECIPES]?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet(),
            aiProvider = p[Keys.AI_PROVIDER] ?: "NONE",
            aiApiKey = p[Keys.AI_API_KEY] ?: "",
            aiModel = p[Keys.AI_MODEL] ?: "",
            aiBaseUrl = p[Keys.AI_BASE_URL] ?: "",
        )
    }

    suspend fun setTheme(theme: AppTheme) = context.dataStore.edit { it[Keys.THEME] = theme.name }
    suspend fun setGroupByAisle(value: Boolean) = context.dataStore.edit { it[Keys.GROUP_BY_AISLE] = value }
    suspend fun setDefaultServings(value: Int) = context.dataStore.edit { it[Keys.DEFAULT_SERVINGS] = value.coerceIn(1, 20) }
    suspend fun setRecipesUrl(value: String) = context.dataStore.edit { it[Keys.RECIPES_URL] = value.trim() }
    suspend fun setSeeded(value: Boolean) = context.dataStore.edit { it[Keys.SEEDED] = value }
    suspend fun setCatalogUrl(value: String) = context.dataStore.edit { it[Keys.CATALOG_URL] = value.trim() }
    suspend fun setCatalogAutoSync(value: Boolean) = context.dataStore.edit { it[Keys.CATALOG_AUTO_SYNC] = value }
    suspend fun setGithubToken(value: String) = context.dataStore.edit { it[Keys.GITHUB_TOKEN] = value.trim() }
    suspend fun setContributorName(value: String) = context.dataStore.edit { it[Keys.CONTRIBUTOR_NAME] = value.trim() }
    suspend fun setRecipeDirty(id: Long, dirty: Boolean) = context.dataStore.edit {
        val cur = it[Keys.DIRTY_RECIPES] ?: emptySet()
        it[Keys.DIRTY_RECIPES] = if (dirty) cur + id.toString() else cur - id.toString()
    }
    suspend fun setAi(provider: String, apiKey: String, model: String, baseUrl: String) = context.dataStore.edit {
        it[Keys.AI_PROVIDER] = provider
        it[Keys.AI_API_KEY] = apiKey.trim()
        it[Keys.AI_MODEL] = model.trim()
        it[Keys.AI_BASE_URL] = baseUrl.trim().trimEnd('/')
    }
    suspend fun setCatalogSynced(version: Int, at: Long) = context.dataStore.edit {
        it[Keys.CATALOG_VERSION] = version
        it[Keys.CATALOG_LAST_SYNC] = at
    }
}
