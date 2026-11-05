package com.tkos.miam.data.update

import com.tkos.miam.BuildConfig
import com.tkos.miam.data.catalog.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Vérification de mise à jour de l'app via la dernière release GitHub
 * (créée par le workflow Build APK à chaque push sur main).
 */
object AppUpdate {
    const val REPO = "Tkos001/miam"
    private const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
    private const val RELEASES_PAGE = "https://github.com/$REPO/releases/latest"

    @Serializable
    internal data class Release(
        val tag_name: String = "",
        val name: String = "",
        val html_url: String = RELEASES_PAGE,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    internal data class Asset(val name: String = "", val browser_download_url: String = "")

    private val json = Json { ignoreUnknownKeys = true }

    data class Info(
        val installed: String,
        val latest: String,
        val isNewer: Boolean,
        /** Lien direct vers l'APK (miam-latest.apk) ou, à défaut, la page de la release. */
        val downloadUrl: String,
    )

    /** Version installée sans suffixe de build (« 0.1.42-debug » → « 0.1.42 »). */
    val installedVersion: String get() = BuildConfig.VERSION_NAME.substringBefore('-')

    suspend fun check(): Info = withContext(Dispatchers.IO) {
        val release = json.decodeFromString(Release.serializer(), Http.getText(LATEST_URL, "application/vnd.github+json"))
        val latest = release.tag_name.removePrefix("v").ifBlank { release.name.substringAfterLast(' ') }
        val apk = release.assets.firstOrNull { it.name == "miam-latest.apk" }
            ?: release.assets.firstOrNull { it.name.endsWith(".apk") }
        Info(
            installed = installedVersion,
            latest = latest,
            isNewer = compare(latest, installedVersion) > 0,
            downloadUrl = apk?.browser_download_url ?: release.html_url,
        )
    }

    /** Compare deux versions « a.b.c » numériquement, segment par segment. */
    fun compare(a: String, b: String): Int {
        val pa = a.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val pb = b.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val d = (pa.getOrNull(i) ?: 0) - (pb.getOrNull(i) ?: 0)
            if (d != 0) return d
        }
        return 0
    }
}
