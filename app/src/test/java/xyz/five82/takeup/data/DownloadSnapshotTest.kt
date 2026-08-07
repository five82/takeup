package xyz.five82.takeup.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A download stores the raw item response and decodes it with [LoomJson.item]. These
 * assertions pin the fields Home's row and an offline details screen depend on, so a
 * parser change cannot quietly strip something only reachable with no server.
 */
class DownloadSnapshotTest {
    @Test
    fun `decodes everything an offline episode needs from its snapshot`() {
        val item = LoomJson.item(EPISODE_SNAPSHOT)

        assertEquals("The Constant", item.title)
        assertEquals("episode", item.kind)
        assertEquals("Lost", item.seriesTitle)
        assertEquals("Season 4", item.seasonTitle)
        assertEquals("S04E05", item.episodeLabel())
        assertEquals("Lost · Season 4 · S04E05", item.episodeContext())
        assertEquals(2_580_000L, item.mediaDurationMs)
        assertEquals("9f86d081884c7d65", item.mediaTag)
        assertEquals(8_123_456_789L, item.mediaSizeBytes)
        assertEquals(2, item.mediaStreams.size)
        assertEquals(MediaDynamicRange.HDR, item.mediaStreams[0].dynamicRange)
        assertEquals("5.1", item.mediaStreams[1].channelLayout)
        assertEquals(600_000L, item.progress?.resumePositionMs)
        assertEquals(listOf("Drama"), item.genres.map { it.name })
    }

    @Test
    fun `keeps artwork identifiers so local art can be matched to the item`() {
        val item = LoomJson.item(EPISODE_SNAPSHOT)

        assertEquals(11L, item.posterImageId)
        assertEquals(12L, item.backdropImageId)
        assertEquals(42L, item.id)
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
                    "dynamic_range": "hdr",
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
