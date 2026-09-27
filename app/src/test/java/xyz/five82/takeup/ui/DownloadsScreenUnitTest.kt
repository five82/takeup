package xyz.five82.takeup.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.data.DownloadEntry
import xyz.five82.takeup.data.DownloadState
import xyz.five82.takeup.data.DownloadStore
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.data.NetworkPolicy
import xyz.five82.takeup.data.Reach
import xyz.five82.takeup.ui.downloads.DownloadsScreen
import xyz.five82.takeup.ui.theme.TakeupTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadsScreenUnitTest {
    @get:Rule val compose = createComposeRule()

    private val repo = Mockito.mock(LoomRepository::class.java)
    private val downloads = Mockito.mock(DownloadStore::class.java)
    private val network = Mockito.mock(NetworkPolicy::class.java)
    private val entries = MutableStateFlow<List<DownloadEntry>>(emptyList())
    private val reach = MutableStateFlow(Reach.Home)

    private fun show() {
        Mockito.doReturn(downloads).`when`(repo).downloads
        Mockito.doReturn(network).`when`(repo).network
        Mockito.doReturn(entries).`when`(downloads).downloads
        Mockito.doReturn(reach).`when`(network).reach
        Mockito.`when`(downloads.usableSpaceBytes()).thenReturn(5_000_000_000)
        compose.setContent { TakeupTheme { DownloadsScreen(repo, NavState()) } }
    }

    private fun entry(id: Long, state: DownloadState) = DownloadEntry(
        item = Item(id = id, title = "Film $id"), state = state,
        uri = "https://loom/stream/$id?tag=one", bytesDownloaded = 1_000_000_000,
        totalBytes = 2_000_000_000,
    )

    @Test fun emptyInventoryExplainsThereAreNoDownloads() {
        show()
        compose.onNodeWithText("0 active · 0 on this device").assertExists()
        compose.onNodeWithText("No downloads on this device.").assertExists()
    }

    @Test fun completedDownloadRequiresConfirmationBeforeRemoval() {
        entries.value = listOf(entry(42, DownloadState.Completed))
        show()
        compose.onNodeWithText("Film 42").assertExists()
        compose.onNodeWithText("0 active · 1 on this device").assertExists()
        compose.onNodeWithText("Remove").performClick()
        compose.onNodeWithText("Remove download?").assertExists()
        Mockito.verify(downloads, Mockito.never()).remove(42)
        compose.onNodeWithText("Keep").performClick()
        Mockito.verify(downloads, Mockito.never()).remove(42)
        compose.onNodeWithText("Remove").performClick()
        compose.onAllNodesWithText("Remove", useUnmergedTree = false)[1].performClick()
        Mockito.verify(downloads).remove(42)
    }

    @Test fun failedDownloadCannotRetryOfflineButQueuedOneCanBeCancelled() {
        entries.value = listOf(entry(1, DownloadState.Failed), entry(2, DownloadState.Queued))
        reach.value = Reach.Offline
        show()
        compose.onNodeWithText("Retry").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performClick()
        Mockito.verify(downloads).remove(2)
        compose.onNodeWithText("2 active · 0 on this device").assertExists()
    }
}
