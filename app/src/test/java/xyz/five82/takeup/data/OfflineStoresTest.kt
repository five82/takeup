package xyz.five82.takeup.data

import org.robolectric.RuntimeEnvironment
import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.api.loomGson
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class OfflineStoresTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test fun itemsSurviveRestartAndCorruptSnapshotsAreIgnored() = runBlocking {
        val store = OfflineItemStore(context)
        val show = Item(id = 301, title = "The Show", kind = "show")
        val season = Item(id = 302, title = "Season One", kind = "season")
        store.save(show, loomGson.toJson(show))
        store.save(season, loomGson.toJson(season))
        store.save(show.copy(title = "Renamed"), loomGson.toJson(show.copy(title = "Renamed")))
        assertEquals(listOf(302L, 301L), store.items.value.map { it.id })

        File(context.filesDir, "offline-items/broken.json").writeText("not json")
        val restarted = OfflineItemStore(context)
        restarted.load()
        assertEquals(setOf("Renamed", "Season One"), restarted.items.value.map { it.title }.toSet())
        restarted.delete(listOf(301L))
        assertEquals(listOf(302L), restarted.items.value.map { it.id })
        restarted.delete(emptyList())
        OfflineItemStore(context).apply { load() }.also {
            assertEquals(listOf(302L), it.items.value.map { item -> item.id })
        }
        Unit
    }

    @Test fun pendingPositionsReplaceEarlierWritesAndPersistUntilCleared() = runBlocking {
        val store = OfflineProgressStore(context)
        store.load()
        assertTrue(store.all().isEmpty())
        store.enqueue(101, 100, 1000)
        store.enqueue(101, 500, 1000)
        store.enqueue(102, 200, 2000)
        assertEquals(PendingProgress(500, 1000), store.pending(101))
        val restarted = OfflineProgressStore(context)
        restarted.load()
        assertEquals(store.all(), restarted.all())
        restarted.clear(101)
        restarted.clear(101)
        assertNull(restarted.pending(101))
        assertEquals(setOf(102L), restarted.all().keys)
        val again = OfflineProgressStore(context)
        again.load()
        assertFalse(again.all().containsKey(101L))
    }

    @Test fun settingsPersistTheAddressAndOfflineLibraryMetadata() = runBlocking {
        val settings = Settings(context)
        assertEquals(null, settings.serverAddress.first())
        assertFalse(settings.allowCellular.first())
        assertFalse(settings.dialogueBoost.first())
        settings.setServerAddress("192.168.1.20:8097")
        settings.setAllowCellular(true)
        settings.setDialogueBoost(true)
        settings.setLibraryKinds(mapOf(7L to "shorts", 8L to "tv"))
        val restarted = Settings(context)
        assertEquals("192.168.1.20:8097", restarted.serverAddress.first())
        assertTrue(restarted.allowCellular.first())
        assertTrue(restarted.dialogueBoost.first())
        assertEquals(mapOf(7L to "shorts", 8L to "tv"), restarted.libraryKinds.first())
    }
}
