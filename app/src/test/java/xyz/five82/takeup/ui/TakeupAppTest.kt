package xyz.five82.takeup.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.five82.takeup.api.LoomApi
import xyz.five82.takeup.data.DownloadEntry
import xyz.five82.takeup.data.DownloadStore
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.data.NetworkPolicy
import xyz.five82.takeup.data.OfflineCatalog
import xyz.five82.takeup.data.Reach
import xyz.five82.takeup.data.ServerConfig
import xyz.five82.takeup.ui.theme.TakeupTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class TakeupAppTest {
    @get:Rule val compose = createComposeRule()
    private val repo = Mockito.mock(LoomRepository::class.java)
    private val api = Mockito.mock(LoomApi::class.java)
    private val network = Mockito.mock(NetworkPolicy::class.java)
    private val downloads = Mockito.mock(DownloadStore::class.java)
    private val server = MutableStateFlow(ServerConfig(false, null))

    private fun show() {
        Mockito.doReturn(server).`when`(repo).server
        Mockito.doReturn(api).`when`(repo).api
        Mockito.doReturn(network).`when`(repo).network
        Mockito.doReturn(MutableStateFlow(Reach.Offline)).`when`(network).reach
        Mockito.doReturn(MutableStateFlow("Loom is unavailable")).`when`(network).reason
        Mockito.doReturn(downloads).`when`(repo).downloads
        Mockito.doReturn(MutableStateFlow(emptyList<DownloadEntry>())).`when`(downloads).downloads
        Mockito.doReturn(MutableStateFlow(OfflineCatalog())).`when`(repo).offlineCatalog
        compose.setContent { TakeupTheme { TakeupApp(repo) } }
        compose.mainClock.advanceTimeBy(500)
    }

    @Test fun waitsForThePersistedAddressBeforeShowingNavigation() {
        show()
        compose.onNodeWithContentDescription("Home").assertDoesNotExist()
        server.value = ServerConfig(true, "192.168.1.20:8097")
        compose.onNodeWithContentDescription("Home").assertExists()
        compose.onNodeWithContentDescription("Movies").assertExists()
    }

    @Test fun phoneNavigationSwitchesLibraryAndPushesSearch() {
        server.value = ServerConfig(true, "192.168.1.20:8097")
        show()
        compose.onNodeWithContentDescription("Movies").performClick()
        compose.onNodeWithText("Movies").assertExists()
        compose.onNodeWithContentDescription("Home").performClick()
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithText("Titles and people").assertExists()
    }
}
