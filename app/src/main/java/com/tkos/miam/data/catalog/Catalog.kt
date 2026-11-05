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
    /** Illustrations pour des recettes locales existantes (ex. plats de base), clé = nom du plat. */
    val images: Map<String, String> = emptyMap(),
)

object Catalog {
    /** Fichier `data/recipes.json` sur la branche main du dépôt. */
    const val DEFAULT_URL = "https://raw.githubusercontent.com/Tkos001/miam/main/data/recipes.json"

    private val json = Json { ignoreUnknownKeys = true }

    private val RAW_GITHUB = Regex("^https://raw\\.githubusercontent\\.com/([^/]+)/([^/]+)/([^/]+)/(.+)$")

    /**
     * Télécharge et parse le catalogue.
     * `bustCache` : le CDN de raw.githubusercontent.com garde 5 min et ignore la query string ; pour une mise à jour
     * forcée on passe par l'API GitHub (contenu brut, non mis en cache), avec repli sur l'URL d'origine.
     */
    suspend fun fetch(url: String, bustCache: Boolean): CatalogFile = withContext(Dispatchers.IO) {
        val text = if (bustCache) {
            val m = RAW_GITHUB.find(url)
            if (m != null) {
                val (owner, repo, branch, path) = m.destructured
                runCatching { Http.getText("https://api.github.com/repos/$owner/$repo/contents/$path?ref=$branch", "application/vnd.github.raw+json") }
                    .getOrElse { Http.getText(url) }
            } else Http.getText(url + (if ('?' in url) "&" else "?") + "t=" + System.currentTimeMillis())
        } else Http.getText(url)
        val file = json.decodeFromString(CatalogFile.serializer(), text)
        require(file.format == "miam-catalog") { "Format inattendu : ${file.format}" }
        file
    }
}

/** Mini client HTTP sans dépendance (HttpURLConnection). */
object Http {
    private fun open(url: String, accept: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("User-Agent", "Miam-Android")
        }

    fun getText(url: String, accept: String = "application/json"): String {
        val conn = open(url, accept)
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** Télécharge un binaire vers `dest` (écrit d'abord dans un .tmp puis renomme). */
    fun download(url: String, dest: java.io.File) {
        val conn = open(url, "image/*")
        val tmp = java.io.File(dest.parentFile, dest.name + ".tmp")
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (!tmp.renameTo(dest)) { tmp.copyTo(dest, overwrite = true); tmp.delete() }
        } catch (e: Exception) {
            tmp.delete(); throw e
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
