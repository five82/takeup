package xyz.five82.takeup.data

import com.google.gson.JsonParseException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class LoomJsonTest {
    @Test
    fun `parses health response`() {
        LoomJson.requireHealthy("""{"status":"ok"}""")
    }

    @Test
    fun `rejects unexpected health response`() {
        assertThrows(JsonParseException::class.java) {
            LoomJson.requireHealthy("""{"status":"starting"}""")
        }
    }

    @Test
    fun `parses movie list`() {
        val movies = LoomJson.items(
            """
            {
              "items": [
                {
                  "id": 42,
                  "library_id": 1,
                  "kind": "movie",
                  "title": "Arrival",
                  "year": 2016,
                  "overview": "A linguist works with the military.",
                  "release_date": "2016-11-11",
                  "poster_image_id": 7,
                  "poster_image_tag": "poster-tag",
                  "backdrop_image_id": 8,
                  "backdrop_image_tag": "backdrop-tag",
                  "added_at": "2026-01-01T00:00:00Z",
                  "updated_at": "2026-01-01T00:00:00Z"
                }
              ],
              "limit": 50,
              "offset": 0
            }
            """.trimIndent(),
        )

        assertEquals(1, movies.size)
        assertEquals(42L, movies.single().id)
        assertEquals("Arrival", movies.single().title)
        assertEquals(2016, movies.single().year)
        assertEquals("2016-11-11", movies.single().releaseDate)
        assertEquals(
            "http://loom.test:8097/api/v1/images/7?tag=poster-tag",
            movies.single().posterUrl("http://loom.test:8097"),
        )
    }

    @Test
    fun `treats a null item list as empty`() {
        assertEquals(emptyList<LoomItem>(), LoomJson.items("""{"items":null}"""))
    }

    @Test
    fun `parses item progress`() {
        val item = LoomJson.item(
            """
            {
              "id": 42,
              "kind": "movie",
              "title": "Arrival",
              "year": 2016,
              "media": {
                "id": 7,
                "duration_ms": 6960000
              },
              "progress": {
                "position_ms": 600000,
                "duration_ms": 6960000,
                "played": false,
                "resume_position_ms": 600000,
                "updated_at": "2026-01-01T00:00:00Z"
              }
            }
            """.trimIndent(),
        )

        assertEquals(6_960_000L, item.mediaDurationMs)
        assertEquals(600000L, item.progress?.resumePositionMs)
        assertFalse(item.progress?.played ?: true)
    }

    @Test
    fun `parses episode hierarchy metadata`() {
        val episode = LoomJson.item(
            """
            {
              "id": 52,
              "kind": "episode",
              "title": "A Double Episode",
              "season_number": 0,
              "episode_number": 1,
              "episode_end_number": 2,
              "release_date": "2026-08-02"
            }
            """.trimIndent(),
        )

        assertEquals(0, episode.seasonNumber)
        assertEquals(1, episode.episodeNumber)
        assertEquals(2, episode.episodeEndNumber)
        assertEquals("2026-08-02", episode.releaseDate)
    }

    @Test
    fun `parses playback response`() {
        val playback = LoomJson.playback(
            """
            {
              "item_id": 42,
              "media": {
                "id": 7,
                "item_id": 42,
                "filename": "Arrival.mkv",
                "duration_ms": 6960000,
                "container": "matroska"
              },
              "stream_url": "/api/v1/media/7"
            }
            """.trimIndent(),
        )

        assertEquals("/api/v1/media/7", playback.streamPath)
        assertEquals(6960000L, playback.durationMs)
        assertEquals("matroska", playback.container)
    }
}
