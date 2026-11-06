package com.tkos.miam.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Mise à jour « dans l'app » : télécharge l'APK de la release puis l'installe via PackageInstaller.
 *
 * - Première fois : Android demande d'autoriser Miam à installer des applis (réglage une fois pour toutes),
 *   puis une confirmation « Mettre à jour ? ».
 * - Ensuite, sur Android 12+ : Miam étant devenu l'installateur de l'app, la mise à jour se fait sans
 *   confirmation (UPDATE_PACKAGES_WITHOUT_USER_ACTION). L'app se ferme à la fin, il suffit de la rouvrir.
 */
object AppInstaller {

    sealed class State {
        data object Idle : State()
        data class Downloading(val progress: Float) : State()
        data object Installing : State()
        data class Failed(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    fun reset() { _state.value = State.Idle }

    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Intent vers le réglage « Installer des applis inconnues » pour Miam. */
    fun unknownSourcesSettingsIntent(context: Context): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${context.packageName}"))

    /** Vrai si Miam est déjà l'installateur enregistré de lui-même (→ mise à jour silencieuse possible sur Android 12+). */
    fun isSelfInstaller(context: Context): Boolean = runCatching {
        val pm = context.packageManager
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) pm.getInstallSourceInfo(context.packageName).installingPackageName
        else @Suppress("DEPRECATION") pm.getInstallerPackageName(context.packageName)
        installer == context.packageName
    }.getOrDefault(false)

    suspend fun downloadAndInstall(context: Context, url: String) {
        if (_state.value is State.Downloading || _state.value is State.Installing) return
        try {
            val apk = download(context, url)
            _state.value = State.Installing
            install(context, apk)
        } catch (e: Exception) {
            _state.value = State.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun download(context: Context, url: String): File = withContext(Dispatchers.IO) {
        _state.value = State.Downloading(0f)
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val dest = File(dir, "miam-update.apk")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 60_000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Miam-Android"); setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); done += n
                        if (total > 0) _state.value = State.Downloading(done.toFloat() / total)
                    }
                }
            }
        } finally { conn.disconnect() }
        val info = context.packageManager.getPackageArchiveInfo(dest.absolutePath, 0) ?: error("APK illisible")
        if (info.packageName != context.packageName) error("APK inattendu : ${info.packageName}")
        dest
    }

    private suspend fun install(context: Context, apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) setInstallReason(PackageManager.INSTALL_REASON_USER)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("miam.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallResultReceiver::class.java).setAction(InstallResultReceiver.ACTION)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pi.intentSender)
        }
    }

    internal fun onResult(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Android veut une confirmation : on ouvre son écran.
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                else _state.value = State.Failed("Confirmation impossible")
            }
            PackageInstaller.STATUS_SUCCESS -> _state.value = State.Idle // l'app va être relancée / fermée par le système
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "code $status"
                _state.value = State.Failed(
                    when (status) {
                        PackageInstaller.STATUS_FAILURE_ABORTED -> "Installation annulée"
                        PackageInstaller.STATUS_FAILURE_CONFLICT -> "Conflit de signature : désinstalle puis réinstalle Miam"
                        PackageInstaller.STATUS_FAILURE_STORAGE -> "Pas assez d'espace"
                        else -> msg
                    }
                )
            }
        }
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION) AppInstaller.onResult(context, intent)
    }

    companion object { const val ACTION = "com.tkos.miam.INSTALL_RESULT" }
}
