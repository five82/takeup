package xyz.five82.takeup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import xyz.five82.takeup.api.Chapter
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.api.MediaFile
import xyz.five82.takeup.api.Progress
import xyz.five82.takeup.api.Stream
import xyz.five82.takeup.ui.audioLayout
import xyz.five82.takeup.ui.episodeLabel
import xyz.five82.takeup.ui.formatClock
import xyz.five82.takeup.ui.formatRuntime
import xyz.five82.takeup.ui.nextEpisodeAfter
import xyz.five82.takeup.ui.progressFraction
import xyz.five82.takeup.ui.remainingLabel
import xyz.five82.takeup.ui.techBadges

class FormatTest {

    @Test
    fun runtimeFormatsHoursAndMinutes() {
        assertEquals("2 h 0 m", formatRuntime(7_200_000))
        assertEquals("58 m", formatRuntime(3_480_000))
        assertEquals("1 h 47 m", formatRuntime(6_420_000))
    }

    @Test
    fun clockFormatsWithAndWithoutHours() {
        assertEquals("1:47:32", formatClock(6_452_000))
        assertEquals("7:32", formatClock(452_000))
        assertEquals("0:00", formatClock(0))
        assertEquals("0:00", formatClock(-500))
    }

    @Test
    fun episodeLabelsIncludeMultiEpisodeFiles() {
        assertEquals("S4E1", episodeLabel(Item(seasonNumber = 4, episodeNumber = 1)))
        assertEquals(
            "S4E1-2",
            episodeLabel(Item(seasonNumber = 4, episodeNumber = 1, episodeEndNumber = 2)),
        )
    }

    @Test
    fun remainingAndFractionComeFromProgress() {
        val item = Item(progress = Progress(positionMs = 1_800_000, durationMs = 3_600_000))
        assertEquals("30 m left", remainingLabel(item))
        assertEquals(0.5f, progressFraction(item)!!, 0.001f)
        assertNull(remainingLabel(Item()))
        assertNull(progressFraction(Item(progress = Progress(played = true, positionMs = 1, durationMs = 2))))
    }

    @Test
    fun techBadgesDeriveFromStreams() {
        val media = MediaFile(
            container = "mkv",
            streams = listOf(
                Stream(kind = "video", codec = "hevc", width = 3840, height = 2160, dynamicRange = "dolby_vision"),
                Stream(kind = "audio", codec = "truehd", channels = 8, channelLayout = "7.1", isDefault = true),
                Stream(kind = "audio", codec = "ac3", channels = 2),
            ),
        )
        assertEquals(listOf("4K", "Dolby Vision", "HEVC", "TrueHD 7.1", "MKV"), techBadges(media))
        assertEquals(emptyList<String>(), techBadges(null))
    }

    @Test
    fun audioLayoutPrefersLayoutStringOverChannelCount() {
        assertEquals("5.1", audioLayout("5.1(side)", 6))
        assertEquals("7.1", audioLayout(null, 8))
        assertEquals("2.0", audioLayout("", 2))
        assertNull(audioLayout(null, 0))
    }

    @Test
    fun nextEpisodeCrossesSeasonsAndSkipsSpecials() {
        val episodes = listOf(
            Item(id = 1, kind = "episode", seasonNumber = 1, episodeNumber = 1),
            Item(id = 2, kind = "episode", seasonNumber = 1, episodeNumber = 2),
            Item(id = 3, kind = "episode", seasonNumber = 2, episodeNumber = 1),
            Item(id = 9, kind = "episode", seasonNumber = 0, episodeNumber = 1),
        )
        assertEquals(2L, nextEpisodeAfter(episodes, 1)!!.id)
        // Season rollover: S1E2 -> S2E1, never the special.
        assertEquals(3L, nextEpisodeAfter(episodes, 2)!!.id)
        assertNull(nextEpisodeAfter(episodes, 3))
        // A special only chains within season zero.
        assertNull(nextEpisodeAfter(episodes, 9))
    }

    @Test
    fun chapterNameFallsBackToNumber() {
        assertEquals("Docking", xyz.five82.takeup.ui.player.chapterName(Chapter(index = 13, title = "Docking")))
        assertEquals("Chapter 14", xyz.five82.takeup.ui.player.chapterName(Chapter(index = 13, title = null)))
    }
}
