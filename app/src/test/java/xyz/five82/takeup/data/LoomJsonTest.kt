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
                  "tmdb_id": 329865,
                  "overview": "A linguist works with the military.",
                  "release_date": "2016-11-11",
                  "poster_image_id": 7,
                  "poster_image_tag": "poster-tag",
                  "backdrop_image_id": 8,
                  "backdrop_image_tag": "backdrop-tag",
                  "logo_image_id": 9,
                  "logo_image_tag": "logo-tag",
                  "thumb_image_id": 10,
                  "thumb_image_tag": "thumb-tag",
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
        assertEquals(329865L, movies.single().tmdbId)
        assertEquals("2016-11-11", movies.single().releaseDate)
        assertEquals(
            "http://loom.test:8097/api/v1/images/7?tag=poster-tag",
            movies.single().posterUrl("http://loom.test:8097"),
        )
        assertEquals(
            "http://loom.test:8097/api/v1/images/9?tag=logo-tag",
            movies.single().logoUrl("http://loom.test:8097"),
        )
        assertEquals(
            "http://loom.test:8097/api/v1/images/10?tag=thumb-tag",
            movies.single().thumbUrl("http://loom.test:8097"),
        )
    }

    @Test
    fun `treats a null item list as empty`() {
        assertEquals(emptyList<LoomItem>(), LoomJson.items("""{"items":null}"""))
    }

    // Without the flat media_tag a listed item has no version to compare against,
    // and a superseded download goes unflagged everywhere except the detail screen.
    @Test
    fun `reads the media version from a list item`() {
        val movies = LoomJson.items(
            """
            {
              "items": [
                {
                  "id": 42,
                  "kind": "movie",
                  "title": "Arrival",
                  "year": 2016,
                  "media_tag": "list-tag",
                  "added_at": "2026-01-01T00:00:00Z",
                  "updated_at": "2026-01-01T00:00:00Z"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals("list-tag", movies.single().mediaTag)
    }

    // A single-item response carries both; they describe the same file version.
    @Test
    fun `prefers the nested media version when a response carries both`() {
        val item = LoomJson.item(
            """
            {
              "id": 42,
              "kind": "movie",
              "title": "Arrival",
              "year": 2016,
              "media_tag": "flat-tag",
              "media": {
                "id": 7,
                "tag": "nested-tag",
                "size": 100,
                "duration_ms": 600000,
                "streams": [
                  {
                    "kind": "video",
                    "codec": "hevc",
                    "width": 3840,
                    "height": 1604,
                    "dynamic_range": "hdr",
                    "is_default": true
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        assertEquals("nested-tag", item.mediaTag)
    }

    @Test
    fun `parses item genres`() {
        val item = LoomJson.item(
            """
            {
              "id": 42,
              "kind": "movie",
              "title": "Arrival",
              "genres": [
                {"id": 878, "name": "Science Fiction"},
                {"id": 53, "name": "Thriller"}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(
            listOf(Genre(878, "Science Fiction"), Genre(53, "Thriller")),
            item.genres,
        )
    }

    @Test
    fun `treats missing item genres as empty`() {
        val item = LoomJson.item("""{"id":42,"kind":"show","title":"Test Show"}""")

        assertEquals(emptyList<Genre>(), item.genres)
    }

    // Loom serves directors ahead of the billed cast, and the pills render in the
    // order they arrive, so the order is part of the contract.
    @Test
    fun `parses item credits in the order Loom serves them`() {
        val item = LoomJson.item(
            """
            {
              "id": 42,
              "kind": "movie",
              "title": "Alien",
              "credits": [
                {"person_id": 578, "name": "Ridley Scott", "role": "director"},
                {
                  "person_id": 10205,
                  "name": "Sigourney Weaver",
                  "role": "actor",
                  "character": "Ripley"
                },
                {"person_id": 4139, "name": "Tom Skerritt", "role": "actor"}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                Credit(578, "Ridley Scott", "director"),
                Credit(10205, "Sigourney Weaver", "actor", "Ripley"),
                // TMDB leaves the character off plenty of entries.
                Credit(4139, "Tom Skerritt", "actor"),
            ),
            item.credits,
        )
    }

    // Episodes are never credited, and no list endpoint carries credits at all.
    @Test
    fun `treats missing item credits as empty`() {
        val item = LoomJson.item("""{"id":42,"kind":"episode","title":"Pilot"}""")

        assertEquals(emptyList<Credit>(), item.credits)
    }

    @Test
    fun `treats null item credits as empty`() {
        val item = LoomJson.item("""{"id":42,"kind":"movie","title":"Arrival","credits":null}""")

        assertEquals(emptyList<Credit>(), item.credits)
    }

    @Test
    fun `rejects credits that are not an array`() {
        assertThrows(JsonParseException::class.java) {
            LoomJson.item("""{"id":42,"kind":"movie","title":"Arrival","credits":{}}""")
        }
    }

    @Test
    fun `parses genre summaries`() {
        val genres = LoomJson.genres(
            """
            {
              "items": [
                {"id": 28, "name": "Action", "item_count": 12},
                {"id": 878, "name": "Science Fiction", "item_count": 4}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                GenreSummary(28, "Action", 12),
                GenreSummary(878, "Science Fiction", 4),
            ),
            genres,
        )
    }

    @Test
    fun `treats a null genre list as empty`() {
        assertEquals(emptyList<GenreSummary>(), LoomJson.genres("""{"items":null}"""))
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
                "tag": "9f86d081884c7d65",
                "duration_ms": 6960000,
                "streams": [
                  {
                    "index": 0,
                    "kind": "video",
                    "codec": "hevc",
                    "profile": "Main 10",
                    "width": 3840,
                    "height": 1604,
                    "dynamic_range": "hdr",
                    "is_default": true
                  },
                  {
                    "index": 1,
                    "kind": "audio",
                    "codec": "opus",
                    "channels": 8,
                    "channel_layout": "7.1",
                    "is_default": true
                  }
                ]
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
        assertEquals(2, item.mediaStreams.size)
        assertEquals("Main 10", item.mediaStreams[0].profile)
        assertEquals(MediaDynamicRange.HDR, item.mediaStreams[0].dynamicRange)
        assertEquals("7.1", item.mediaStreams[1].channelLayout)
        assertEquals(600000L, item.progress?.resumePositionMs)
        assertFalse(item.progress?.played ?: true)
        assertEquals("9f86d081884c7d65", item.mediaTag)
    }

    @Test
    fun `rejects media from Loom without technical stream metadata`() {
        assertThrows(JsonParseException::class.java) {
            LoomJson.item(
                """
                {
                  "id": 42,
                  "kind": "movie",
                  "title": "Arrival",
                  "media": {
                    "duration_ms": 6960000,
                    "streams": [
                      {
                        "kind": "video",
                        "codec": "hevc",
                        "width": 3840,
                        "height": 1604,
                        "is_default": true
                      }
                    ]
                  }
                }
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `parses season poster artwork`() {
        val season = LoomJson.item(
            """
            {
              "id": 266,
              "kind": "season",
              "title": "Season 1",
              "parent_id": 265,
              "season_number": 1,
              "poster_image_id": 861,
              "poster_image_tag": "season-poster-tag"
            }
            """.trimIndent(),
        )

        assertEquals(861L, season.posterImageId)
        assertEquals("season-poster-tag", season.posterImageTag)
        assertEquals(
            "http://loom.test:8097/api/v1/images/861?tag=season-poster-tag",
            season.posterUrl("http://loom.test:8097"),
        )
    }

    @Test
    fun `parses episode hierarchy metadata`() {
        val episode = LoomJson.item(
            """
            {
              "id": 52,
              "kind": "episode",
              "title": "A Double Episode",
              "parent_id": 51,
              "season_number": 0,
              "episode_number": 1,
              "episode_end_number": 2,
              "release_date": "2026-08-02"
            }
            """.trimIndent(),
        )

        assertEquals(51L, episode.parentId)
        assertEquals(0, episode.seasonNumber)
        assertEquals(1, episode.episodeNumber)
        assertEquals(2, episode.episodeEndNumber)
        assertEquals("2026-08-02", episode.releaseDate)
    }

    @Test
    fun `parses the episode rollup on a show`() {
        val show = LoomJson.item(
            """
            {
              "id": 265,
              "kind": "show",
              "title": "Severance",
              "episode_count": 19,
              "unwatched_count": 4
            }
            """.trimIndent(),
        )

        assertEquals(19, show.episodeCount)
        assertEquals(4, show.unwatchedCount)
    }

    @Test
    fun `treats an omitted episode rollup as zero`() {
        val show = LoomJson.item(
            """
            {
              "id": 265,
              "kind": "show",
              "title": "Severance",
              "episode_count": 19
            }
            """.trimIndent(),
        )

        assertEquals(19, show.episodeCount)
        assertEquals(0, show.unwatchedCount)
    }

    @Test
    fun `parses the detail fields on a show`() {
        val show = LoomJson.item(
            """
            {
              "id": 265,
              "kind": "show",
              "title": "Severance",
              "tagline": "Who are you at work?",
              "vote_average": 8.4,
              "content_rating": "TV-MA",
              "status": "Returning Series",
              "total_seasons": 2
            }
            """.trimIndent(),
        )

        assertEquals("Who are you at work?", show.tagline)
        assertEquals(8.4, show.voteAverage, 0.001)
        assertEquals("TV-MA", show.contentRating)
        assertEquals("Returning Series", show.status)
        assertEquals(2, show.totalSeasons)
    }

    @Test
    fun `parses the detail fields on a movie`() {
        val movie = LoomJson.item(
            """
            {
              "id": 42,
              "kind": "movie",
              "title": "Arrival",
              "tagline": "Why are they here?",
              "vote_average": 7.6,
              "content_rating": "PG-13"
            }
            """.trimIndent(),
        )

        assertEquals("Why are they here?", movie.tagline)
        assertEquals(7.6, movie.voteAverage, 0.001)
        assertEquals("PG-13", movie.contentRating)
        // Loom stores neither for a movie, whose status is "Released" for all
        // but a handful and says nothing a screen can use.
        assertEquals("", movie.status)
        assertEquals(0, movie.totalSeasons)
    }

    @Test
    fun `treats omitted detail fields as blank`() {
        val item = LoomJson.item("""{"id":42,"kind":"movie","title":"Arrival"}""")

        assertEquals("", item.tagline)
        assertEquals(0.0, item.voteAverage, 0.001)
        assertEquals("", item.contentRating)
        assertEquals("", item.status)
        assertEquals(0, item.totalSeasons)
    }

    @Test
    fun `parses artwork options`() {
        val options = LoomJson.artworkOptions(
            """
            {
              "items": [
                {
                  "provider": "tmdb",
                  "provider_path": "/poster.jpg",
                  "language": "en",
                  "width": 1000,
                  "height": 1500,
                  "thumbnail_url": "https://image.tmdb.org/t/p/w342/poster.jpg",
                  "selected": true
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, options.size)
        assertEquals("/poster.jpg", options.single().providerPath)
        assertEquals("en", options.single().language)
        assertEquals(1000, options.single().width)
        assertEquals(true, options.single().selected)
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
                "size": 42949672960,
                "tag": "9f86d081884c7d65",
                "duration_ms": 6960000,
                "container": "matroska"
              },
              "stream_url": "/api/v1/media/7?tag=9f86d081884c7d65"
            }
            """.trimIndent(),
        )

        assertEquals("/api/v1/media/7?tag=9f86d081884c7d65", playback.streamPath)
        assertEquals(6960000L, playback.durationMs)
        assertEquals("matroska", playback.container)
        assertEquals("9f86d081884c7d65", playback.tag)
        assertEquals(42_949_672_960L, playback.sizeBytes)
    }

    @Test
    fun `parses chapter marks from a playback response`() {
        val playback = LoomJson.playback(
            """
            {
              "item_id": 42,
              "media": {
                "id": 7,
                "tag": "9f86d081884c7d65",
                "duration_ms": 6960000,
                "container": "matroska",
                "chapters": [
                  {"index": 0, "start_ms": 0, "title": "Chapter 01"},
                  {"index": 1, "start_ms": 300341, "title": "Chapter 02"},
                  {"index": 2, "start_ms": 500291, "title": "Chapter 03"}
                ]
              },
              "stream_url": "/api/v1/media/7?tag=9f86d081884c7d65"
            }
            """.trimIndent(),
        )

        assertEquals(listOf(0L, 300341L, 500291L), playback.chapterStartsMs)
    }

    // Loom omits chapters for a file that has none, and for one whose single
    // chapter spans the whole runtime.
    @Test
    fun `treats media without chapters as having none`() {
        val playback = LoomJson.playback(
            """
            {
              "item_id": 42,
              "media": {
                "id": 7,
                "tag": "9f86d081884c7d65",
                "duration_ms": 6960000,
                "container": "matroska"
              },
              "stream_url": "/api/v1/media/7?tag=9f86d081884c7d65"
            }
            """.trimIndent(),
        )

        assertEquals(emptyList<Long>(), playback.chapterStartsMs)
    }

    // A download plays from the item snapshot taken when it was queued, so the
    // item response has to carry the marks too.
    @Test
    fun `parses chapter marks from an item response`() {
        val item = LoomJson.item(
            """
            {
              "id": 42,
              "kind": "movie",
              "title": "Arrival",
              "year": 2016,
              "media": {
                "id": 7,
                "tag": "9f86d081884c7d65",
                "duration_ms": 6960000,
                "streams": [
                  {
                    "kind": "video",
                    "codec": "hevc",
                    "width": 3840,
                    "height": 1604,
                    "dynamic_range": "hdr",
                    "is_default": true
                  }
                ],
                "chapters": [
                  {"index": 0, "start_ms": 0, "title": "Chapter 01"},
                  {"index": 1, "start_ms": 300341, "title": "Chapter 02"}
                ]
              }
            }
            """.trimIndent(),
        )

        assertEquals(listOf(0L, 300341L), item.mediaChapterStartsMs)
    }

    // A collection nests its resolved members under the same "items" key the
    // response itself uses, so the inner list must not be read as the outer one.
    @Test
    fun `parses collections with their members`() {
        val collections = LoomJson.collections(
            """
            {
              "items": [
                {
                  "slug": "star-wars",
                  "title": "Star Wars",
                  "items": [
                    {"id": 11, "kind": "movie", "title": "Star Wars", "year": 1977},
                    {"id": 12, "kind": "movie", "title": "The Empire Strikes Back", "year": 1980}
                  ]
                },
                {
                  "slug": "kill-bill",
                  "title": "Kill Bill",
                  "items": [
                    {"id": 24, "kind": "movie", "title": "Kill Bill: Vol. 1", "year": 2003}
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(listOf("star-wars", "kill-bill"), collections.map { it.slug })
        assertEquals("Star Wars", collections.first().title)
        assertEquals(
            listOf("Star Wars", "The Empire Strikes Back"),
            collections.first().items.map { it.title },
        )
        assertEquals(1977, collections.first().items.first().year)
    }

    @Test
    fun `rejects a playback response without a media version tag`() {
        assertThrows(JsonParseException::class.java) {
            LoomJson.playback(
                """
                {
                  "item_id": 42,
                  "media": {
                    "id": 7,
                    "filename": "Arrival.mkv",
                    "size": 42949672960,
                    "duration_ms": 6960000,
                    "container": "matroska"
                  },
                  "stream_url": "/api/v1/media/7"
                }
                """.trimIndent(),
            )
        }
    }
}
