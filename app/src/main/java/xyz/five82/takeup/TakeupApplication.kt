package xyz.five82.takeup

import android.app.Application
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.data.ServerPreferences

class TakeupApplication : Application() {
    internal lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

internal class AppContainer(application: Application) {
    val loomRepository = LoomRepository(ServerPreferences(application))
}
