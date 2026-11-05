package com.tkos.miam.data.catalog

import com.tkos.miam.data.backup.RecipeDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * Catalogue de recettes distant : un fichier JSON versionné dans le dépôt git.
 * L'app le télécharge et insère / met à jour les recettes par `remoteId`.
 */
@Serializable
data class CatalogFile(
    val format: String = "miam-catalog",
    val version: Int = 0,
    val updatedAt: String = "",
    val recipes: List<RecipeDto> = emptyList(),
)

object Catalog {
    /** Fichier `data/recipes.json` sur la branche main du dépôt. */
    const val DEFAULT_URL = "https://raw.githubusercontent.com/Tkos001/miam/main/data/recipes.json"

    private val json = Json { ignoreUnknownKeys = true }

    /** Télécharge et parse le catalogue. `bustCache` ajoute un paramètre pour contourner le cache CDN (~5 min). */
    suspend fun fetch(url: String, bustCache: Boolean): CatalogFile = withContext(Dispatchers.IO) {
        val full = if (bustCache) url + (if ('?' in url) "&" else "?") + "t=" + System.currentTimeMillis() else url
        val text = Http.getText(full)
        val file = json.decodeFromString(CatalogFile.serializer(), text)
        require(file.format == "miam-catalog") { "Format inattendu : ${file.format}" }
        file
    }
}

/** Mini client HTTP sans dépendance (HttpURLConnection). */
object Http {
    fun getText(url: String, accept: String = "application/json"): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", accept)
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("User-Agent", "Miam-Android")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

sealed class CatalogSyncResult {
    /** Catalogue déjà à jour (même version). */
    data class UpToDate(val version: Int) : CatalogSyncResult()
    data class Updated(val version: Int, val added: Int, val updated: Int) : CatalogSyncResult()
    data class Failed(val message: String) : CatalogSyncResult()
}
