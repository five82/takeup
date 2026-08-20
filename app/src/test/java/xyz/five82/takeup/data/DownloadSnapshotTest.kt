package xyz.five82.takeup.data

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.api.loomGson

/**
 * A download stores the raw item response and decodes it with the shared Gson.
 * These assertions pin the fields the offline Home row and player depend on, so
 * a DTO change cannot quietly strip something only reachable with no server.
 */
class DownloadSnapshotTest {
    @Test
    fun `decodes everything an offline episode needs from its snapshot`() {
        val item = loomGson.fromJson(EPISODE_SNAPSHOT, Item::class.java)

        assertEquals(42L, item.id)
        assertEquals("The Constant", item.title)
        assertEquals("episode", item.kind)
        assertEquals(4, item.seasonNumber)
        assertEquals(5, item.episodeNumber)
        assertEquals("Lost", item.seriesTitle)
        assertEquals("Season 4", item.seasonTitle)
        assertEquals("9f86d081884c7d65", item.mediaTag)
        assertEquals(8_123_456_789L, item.media?.size)
        assertEquals(2_580_000L, item.media?.durationMs)
        assertEquals(2, item.media?.streams?.size)
        assertEquals("4k", item.media?.streams?.get(0)?.resolution)
        assertEquals("HDR10", item.media?.streams?.get(0)?.dynamicRange)
        assertEquals("5.1", item.media?.streams?.get(1)?.channelLayout)
        assertEquals(600_000L, item.progress?.resumePositionMs)
        assertEquals(listOf("Drama"), item.genres?.map { it.name })
    }

    @Test
    fun `keeps artwork identifiers so local art can be matched to the item`() {
        val item = loomGson.fromJson(EPISODE_SNAPSHOT, Item::class.java)

        assertEquals(11L, item.posterImageId)
        assertEquals("poster-tag", item.posterImageTag)
        assertEquals(12L, item.backdropImageId)
        assertEquals(13L, item.thumbImageId)
    }

    private companion object {
        val EPISODE_SNAPSHOT = """
            {
              "id": 42,
              "kind": "episode",
              "title": "The Constant",
              "year": 2008,
              "overview": "Desmond experiences side effects.",
              "parent_id": 9,
              "season_number": 4,
              "episode_number": 5,
              "series_title": "Lost",
              "season_title": "Season 4",
              "poster_image_id": 11,
              "poster_image_tag": "poster-tag",
              "backdrop_image_id": 12,
              "backdrop_image_tag": "backdrop-tag",
              "thumb_image_id": 13,
              "thumb_image_tag": "thumb-tag",
              "media_tag": "9f86d081884c7d65",
              "genres": [{"id": 3, "name": "Drama"}],
              "media": {
                "id": 7,
                "tag": "9f86d081884c7d65",
                "size": 8123456789,
                "duration_ms": 2580000,
                "container": "matroska",
                "streams": [
                  {
                    "index": 0,
                    "kind": "video",
                    "codec": "hevc",
                    "profile": "Main 10",
                    "width": 3840,
                    "height": 2160,
                    "resolution": "4k",
                    "dynamic_range": "HDR10",
                    "is_default": true
                  },
                  {
                    "index": 1,
                    "kind": "audio",
                    "codec": "eac3",
                    "channels": 6,
                    "channel_layout": "5.1",
                    "is_default": true
                  }
                ]
              },
              "progress": {
                "position_ms": 600000,
                "duration_ms": 2580000,
                "played": false,
                "resume_position_ms": 600000
              }
            }
        """.trimIndent()
    }
}
