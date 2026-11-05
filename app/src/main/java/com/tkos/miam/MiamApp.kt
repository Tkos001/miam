package com.tkos.miam

import android.app.Application
import com.tkos.miam.data.MiamRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MiamApp : Application() {
    val repository: MiamRepository by lazy { MiamRepository.get(this) }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var lastBackgroundSync = 0L
    private val seeded = CompletableDeferred<Unit>()

    override fun onCreate() {
        super.onCreate()
        appScope.launch { repository.seedIfNeeded(); seeded.complete(Unit) }
        syncInBackground()
    }

    /**
     * Sync silencieuse du catalogue + vérification de mise à jour de l'app.
     * Appelée au démarrage et à chaque retour au premier plan, au plus une fois toutes les 5 min.
     */
    fun syncInBackground() {
        val now = System.currentTimeMillis()
        if (now - lastBackgroundSync < 5 * 60_000L) return
        lastBackgroundSync = now
        appScope.launch {
            seeded.await()
            if (repository.settings.settings.first().catalogAutoSync) repository.syncCatalog(force = false)
            repository.checkAppUpdate()
        }
    }
}
