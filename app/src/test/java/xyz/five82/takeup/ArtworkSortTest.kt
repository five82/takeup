package xyz.five82.takeup

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.five82.takeup.api.ImageOption
import xyz.five82.takeup.ui.artwork.applySelection
import xyz.five82.takeup.ui.artwork.sortArtworkOptions

class ArtworkSortTest {
    @Test
    fun sortsByResolutionBeforeRatingAndVotes() {
        val lowRated4k = option("4k-low-rated", 3840, 2160, rating = 4.0, votes = 1)
        val highRated1080p = option("1080p-high-rated", 1920, 1080, rating = 10.0, votes = 100)
        val moreVotes = option("4k-more-votes", 3840, 2160, rating = 8.0, votes = 20)
        val fewerVotes = option("4k-fewer-votes", 3840, 2160, rating = 8.0, votes = 2)

        val sorted = sortArtworkOptions(
            listOf(lowRated4k, highRated1080p, fewerVotes, moreVotes),
        )

        assertEquals(
            listOf("4k-more-votes", "4k-fewer-votes", "4k-low-rated", "1080p-high-rated"),
            sorted.map { it.providerPath },
        )
    }

    @Test
    fun applySelectionMovesTheSelectedFlagToTheChosenOption() {
        val previouslySelected = option("old", 2000, 3000, rating = 8.0, votes = 5).copy(selected = true)
        val chosen = option("new", 2000, 3000, rating = 7.0, votes = 3)
        val bystander = option("other", 2000, 3000, rating = 6.0, votes = 1)

        val updated = applySelection(listOf(previouslySelected, chosen, bystander), chosen)

        assertEquals(listOf(false, true, false), updated.map { it.selected })
    }

    private fun option(
        path: String,
        width: Int,
        height: Int,
        rating: Double,
        votes: Int,
    ) = ImageOption(
        providerPath = path,
        width = width,
        height = height,
        voteAverage = rating,
        voteCount = votes,
    )
}
