package xyz.five82.takeup

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import xyz.five82.takeup.api.LoomApi
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.data.Settings

class TakeupApplication : Application() {

    /** Outlives any screen; used for work that must finish after a ViewModel dies. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository: LoomRepository by lazy {
        LoomRepository(Settings(this), LoomApi(), appScope)
    }
}
