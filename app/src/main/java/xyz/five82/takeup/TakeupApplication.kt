package xyz.five82.takeup

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import xyz.five82.takeup.data.DownloadStore
import xyz.five82.takeup.data.LoomClient
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.data.NetworkMonitor
import xyz.five82.takeup.data.OfflineArtwork
import xyz.five82.takeup.data.OfflineProgressStore
import xyz.five82.takeup.data.ServerPreferences

class TakeupApplication : Application(), SingletonImageLoader.Factory {
    internal lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .crossfade(true)
            .build()
}

internal class AppContainer(application: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val client = LoomClient()

    val offlineArtwork = OfflineArtwork(application, client)
    val offlineProgress = OfflineProgressStore(application)
    val networkMonitor = NetworkMonitor(application)

    // SimpleCache refuses a second instance over the same directory, so this single
    // owner is what keeps the download cache valid for the whole process.
    val downloadStore = DownloadStore(application, scope, offlineArtwork)

    val loomRepository = LoomRepository(
        preferences = ServerPreferences(application),
        client = client,
        downloads = downloadStore,
        offlineArtwork = offlineArtwork,
        offlineProgress = offlineProgress,
    )

    init {
        scope.launch { offlineProgress.load() }
    }
}
