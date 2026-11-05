package com.tkos.miam.data.catalog

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.tkos.miam.data.backup.BackupCodec
import com.tkos.miam.data.backup.RecipeDto
import com.tkos.miam.data.model.RecipeWithDetails
import com.tkos.miam.data.shopping.ShoppingAggregator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * Publication d'une recette dans le catalogue git via l'API GitHub « contents »
 * (un commit par publication). Nécessite un token fine-grained avec Contents: read/write sur le dépôt.
 */
object CatalogPublisher {

    data class Target(val owner: String, val repo: String, val branch: String, val path: String) {
        val imagesDir: String get() = path.substringBeforeLast('/', "").let { if (it.isBlank()) "images" else "$it/images" }
        fun rawUrl(p: String) = "https://raw.githubusercontent.com/$owner/$repo/$branch/$p"
    }

    data class Result(val remoteId: String, val version: Int, val imageUrl: String?)

    private val RAW = Regex("^https://raw\\.githubusercontent\\.com/([^/]+)/([^/]+)/([^/]+)/(.+)$")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    fun targetOf(catalogUrl: String): Target? = RAW.find(catalogUrl)?.destructured?.let { (o, r, b, p) -> Target(o, r, b, p) }

    @Serializable
    internal class ContentResponse(val sha: String = "", val content: String = "", val encoding: String = "")

    @Serializable
    internal class PutBody(val message: String, val content: String, val branch: String, val sha: String? = null)

    private fun api(target: Target, path: String) =
        "https://api.github.com/repos/${target.owner}/${target.repo}/contents/$path"

    private fun open(url: String, token: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Miam-Android")
        }

    private fun readBody(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun explain(code: Int, body: String): String = when (code) {
        401 -> "Token refusé (401)"
        403 -> "Accès interdit (403) : le token n'a pas Contents : read/write sur ce dépôt"
        404 -> "Dépôt ou fichier introuvable (404) : vérifie le token et l'URL du catalogue"
        409 -> "Conflit (409) : quelqu'un vient de publier, réessaie"
        422 -> "Rejeté par GitHub (422) : ${body.take(120)}"
        else -> "HTTP $code : ${body.take(120)}"
    }

    /** Lit un fichier : (octets, sha) ou null s'il n'existe pas. */
    private fun getFile(target: Target, path: String, token: String): Pair<ByteArray, String>? {
        val conn = open(api(target, path) + "?ref=${target.branch}", token, "GET")
        try {
            val code = conn.responseCode
            if (code == 404) return null
            val body = readBody(conn)
            if (code !in 200..299) error(explain(code, body))
            val r = json.decodeFromString(ContentResponse.serializer(), body)
            return Base64.decode(r.content.replace("\n", ""), Base64.DEFAULT) to r.sha
        } finally {
            conn.disconnect()
        }
    }

    private fun putFile(target: Target, path: String, token: String, bytes: ByteArray, sha: String?, message: String) {
        val conn = open(api(target, path), token, "PUT")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        try {
            val body = json.encodeToString(PutBody.serializer(), PutBody(message, Base64.encodeToString(bytes, Base64.NO_WRAP), target.branch, sha))
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val resp = readBody(conn)
            if (code !in 200..299) error(explain(code, resp))
        } finally {
            conn.disconnect()
        }
    }

    fun slugOf(name: String): String = ShoppingAggregator.normalizeName(name)
        .replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "recette" }

    /** Image locale réduite (max 1200 px) en JPEG. */
    private fun encodeImage(path: String): ByteArray? {
        val file = File(path)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
        val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale = 1200f / maxOf(bmp.width, bmp.height)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
        return out.toByteArray()
    }

    /**
     * Publie la recette : met à jour `recipes.json` (entrée identifiée par `remoteId`, sinon par le nom, sinon ajoutée),
     * incrémente `version`, et envoie la photo locale si elle n'est pas déjà une image du catalogue.
     * Le fichier est relu juste avant l'écriture : les publications sur des recettes différentes ne s'écrasent pas.
     */
    suspend fun publish(d: RecipeWithDetails, catalogUrl: String, token: String): Result = withContext(Dispatchers.IO) {
        val target = targetOf(catalogUrl) ?: error("L'URL du catalogue doit être une URL raw.githubusercontent.com")
        val key = ShoppingAggregator.normalizeName(d.recipe.name)

        var attempt = 0
        while (true) {
            attempt++
            val (bytes, sha) = getFile(target, target.path, token) ?: (null to null)
            val catalog = bytes?.let { json.decodeFromString(CatalogFile.serializer(), it.toString(Charsets.UTF_8)) } ?: CatalogFile()

            val existing = catalog.recipes.firstOrNull { it.remoteId == d.recipe.remoteId && d.recipe.remoteId != null }
                ?: catalog.recipes.firstOrNull { ShoppingAggregator.normalizeName(it.name) == key }
            val remoteId = existing?.remoteId ?: d.recipe.remoteId ?: run {
                val base = slugOf(d.recipe.name)
                val taken = catalog.recipes.mapNotNull { it.remoteId }.toSet()
                var s = base; var n = 2
                while (s in taken) { s = "$base-$n"; n++ }
                s
            }

            // Photo : envoyée si c'est une photo locale (pas une image déjà descendue du catalogue).
            val localPhoto = d.recipe.imagePath?.takeIf { !File(it).name.startsWith("catalog-") }
            var imageUrl = existing?.imageUrl ?: catalog.images.entries.firstOrNull { ShoppingAggregator.normalizeName(it.key) == key }?.value
            if (localPhoto != null) {
                encodeImage(localPhoto)?.let { img ->
                    val imgPath = "${target.imagesDir}/$remoteId.jpg"
                    val cur = runCatching { getFile(target, imgPath, token) }.getOrNull()
                    putFile(target, imgPath, token, img, cur?.second, "Miam : photo de « ${d.recipe.name} »")
                    imageUrl = target.rawUrl(imgPath)
                }
            }

            val dto: RecipeDto = BackupCodec.toDto(d).copy(isFavorite = false, sourceUrl = null, remoteId = remoteId, imageUrl = imageUrl)
            val recipes = catalog.recipes.filterNot { it === existing }.toMutableList()
            val idx = catalog.recipes.indexOf(existing)
            if (idx >= 0) recipes.add(idx, dto) else recipes.add(dto)
            val updated = catalog.copy(
                version = catalog.version + 1,
                updatedAt = LocalDate.now().toString(),
                recipes = recipes,
                images = catalog.images.filterKeys { ShoppingAggregator.normalizeName(it) != key },
            )
            val text = json.encodeToString(CatalogFile.serializer(), updated) + "\n"
            try {
                putFile(target, target.path, token, text.toByteArray(Charsets.UTF_8), sha,
                    "Miam : « ${d.recipe.name} » publiée depuis l'app (catalogue v${updated.version})")
                return@withContext Result(remoteId, updated.version, imageUrl)
            } catch (e: IllegalStateException) {
                if (attempt < 3 && e.message?.startsWith("Conflit") == true) continue
                throw e
            }
        }
    }
}
