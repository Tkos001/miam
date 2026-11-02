package com.tkos.miam

import android.app.Application
import com.tkos.miam.data.MiamRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MiamApp : Application() {
    val repository: MiamRepository by lazy { MiamRepository.get(this) }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch { repository.seedIfNeeded() }
    }
}
