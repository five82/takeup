package xyz.five82.takeup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.five82.takeup.api.Credit
import xyz.five82.takeup.api.FeaturedPick
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.api.LoomApi
import xyz.five82.takeup.api.SearchResponse
import xyz.five82.takeup.api.loomGson

class LoomApiTest {

    @Test
    fun normalizeAddressAcceptsBareHostAndPort() {
        assertEquals("http://192.168.1.20:8097", LoomApi.normalizeAddress("192.168.1.20:8097"))
        assertEquals("http://loom:8097", LoomApi.normalizeAddress(" loom:8097 "))
        assertEquals("http://loom:8097", LoomApi.normalizeAddress("http://loom:8097/"))
        assertEquals("https://loom.lan", LoomApi.normalizeAddress("https://loom.lan"))
        assertNull(LoomApi.normalizeAddress(""))
        assertNull(LoomApi.normalizeAddress("   "))
        assertNull(LoomApi.normalizeAddress("http://"))
    }

    @Test
    fun imageUrlSnapsWidthToServerBuckets() {
        val api = LoomApi("http://loom:8097")
        assertEquals(
            "http://loom:8097/api/v1/images/7?width=480&tag=abc",
            api.imageUrl(7, "abc", 300),
        )
        assertEquals(
            "http://loom:8097/api/v1/images/7?width=240&tag=abc",
            api.imageUrl(7, "abc", 240),
        )
        // Above the largest bucket the request stays at the ceiling.
        assertEquals(
            "http://loom:8097/api/v1/images/7?width=1440&tag=abc",
            api.imageUrl(7, "abc", 2000),
        )
        // Missing image ids and missing servers produce no URL, not a broken one.
        assertNull(api.imageUrl(0, "abc", 480))
        assertNull(LoomApi(null).imageUrl(7, "abc", 480))
        // An empty tag is omitted entirely.
        assertEquals("http://loom:8097/api/v1/images/7?width=480", api.imageUrl(7, "", 480))
    }

    @Test
    fun absoluteUrlJoinsServerRelativePaths() {
        val api = LoomApi("http://loom:8097")
        assertEquals(
            "http://loom:8097/api/v1/media/3?tag=x",
            api.absoluteUrl("/api/v1/media/3?tag=x"),
        )
        assertEquals("http://other/a", api.absoluteUrl("http://other/a"))
    }

    @Test
    fun featuredPickMapsNestedItemAndPeriod() {
        val pick = loomGson.fromJson(
            """{"item":{"id":42,"title":"A Movie","kind":"movie"},"starts_at":"2025-08-12T06:00:00Z","ends_at":"2025-08-12T18:00:00Z"}""",
            FeaturedPick::class.java,
        )

        assertEquals(42L, pick.item.id)
        assertEquals("A Movie", pick.item.title)
        assertEquals("2025-08-12T06:00:00Z", pick.startsAt)
        assertEquals("2025-08-12T18:00:00Z", pick.endsAt)
    }

    @Test
    fun searchResponseMapsFuzzyFallback() {
        val response = loomGson.fromJson(
            """{"items":[{"id":42,"kind":"movie","title":"Spider-Man"}],"fuzzy":true}""",
            SearchResponse::class.java,
        )
        assertTrue(response.fuzzy)
        assertEquals(listOf("Spider-Man"), response.items.map { it.title })
    }

    @Test
    fun creditDisplayTitlesDistinguishCrewFromCast() {
        assertEquals("Director", Credit(role = "director").displayTitle)
        assertEquals("Producer", Credit(role = "producer").displayTitle)
        assertEquals("Indiana Jones", Credit(role = "actor", character = "Indiana Jones").displayTitle)
    }

    @Test
    fun itemJsonMapsSnakeCaseFields() {
        val json = """
            {
              "id": 42, "library_id": 1, "parent_id": 7, "kind": "episode",
              "title": "Fun Run", "season_number": 4, "episode_number": 1,
              "episode_end_number": 2, "tmdb_id": 2316,
              "content_rating": "TV-14", "vote_average": 8.1,
              "poster_image_id": 9, "poster_image_tag": "p1",
              "logo_image_id": 11, "logo_image_tag": "l1",
              "media_tag": "m1", "episode_count": 0,
              "media": {
                "id": 5, "item_id": 42, "filename": "a.mkv", "size": 123,
                "tag": "t", "duration_ms": 1320000, "container": "mkv",
                "streams": [
                  {"index": 0, "kind": "video", "codec": "hevc", "width": 1920,
                   "height": 800, "resolution": "1080p", "dynamic_range": "sdr", "is_default": true}
                ],
                "chapters": [{"index": 0, "start_ms": 0, "title": "Open"}]
              },
              "progress": {"position_ms": 60000, "duration_ms": 1320000,
                           "played": false, "resume_position_ms": 60000}
            }
        """.trimIndent()
        val item = loomGson.fromJson(json, Item::class.java)
        assertEquals(42L, item.id)
        assertEquals(7L, item.parentId)
        assertEquals(4, item.seasonNumber)
        assertEquals(2, item.episodeEndNumber)
        assertEquals("TV-14", item.contentRating)
        assertEquals(9L, item.posterImageId)
        assertEquals("l1", item.logoImageTag)
        assertEquals("m1", item.mediaTag)
        assertEquals(1_320_000L, item.media!!.durationMs)
        assertEquals("sdr", item.media.streams!!.single().dynamicRange)
        assertEquals("1080p", item.media.streams.single().resolution)
        assertTrue(item.media.streams.single().isDefault)
        assertEquals(0L, item.media.chapters!!.single().startMs)
        assertEquals(60_000L, item.progress!!.resumePositionMs)
        // Fields Loom omits stay at their defaults instead of going null.
        assertEquals(0, item.unwatchedCount)
        assertNull(item.overview)
    }
}
