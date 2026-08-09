package xyz.five82.takeup

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import xyz.five82.takeup.api.LoomApi
import xyz.five82.takeup.data.DownloadStore
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.data.OfflineArtwork
import xyz.five82.takeup.data.OfflineProgressStore
import xyz.five82.takeup.data.Settings

class TakeupApplication : Application() {

    /** Outlives any screen; used for work that must finish after a ViewModel dies. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository: LoomRepository by lazy {
        val api = LoomApi()
        val offlineProgress = OfflineProgressStore(this)
        appScope.launch { offlineProgress.load() }
        LoomRepository(
            Settings(this),
            api,
            appScope,
            DownloadStore(this, appScope, OfflineArtwork(this, api)),
            offlineProgress,
        )
    }
}
