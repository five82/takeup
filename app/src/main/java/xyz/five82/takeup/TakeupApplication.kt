package xyz.five82.takeup

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import xyz.five82.takeup.data.LoomRepository
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
    val loomRepository = LoomRepository(ServerPreferences(application))
}
