package xyz.five82.takeup

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import xyz.five82.takeup.api.LoomApi
import xyz.five82.takeup.data.DownloadStore
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.data.NetworkPolicy
import xyz.five82.takeup.data.OfflineArtwork
import xyz.five82.takeup.data.OfflineProgressStore
import xyz.five82.takeup.data.Settings

class TakeupApplication : Application(), SingletonImageLoader.Factory {

    /** Outlives any screen; used for work that must finish after a ViewModel dies. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository: LoomRepository by lazy {
        val settings = Settings(this)
        val network = NetworkPolicy(this, settings, appScope)
        val api = LoomApi(blocked = { network.blocked.value })
        val offlineProgress = OfflineProgressStore(this)
        appScope.launch { offlineProgress.load() }
        LoomRepository(
            settings,
            api,
            appScope,
            DownloadStore(this, appScope, OfflineArtwork(this, api), network),
            offlineProgress,
            network,
        )
    }

    /** Coil borrows the API's client so artwork passes the same network gate. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { repository.api.client })) }
            .build()
}
