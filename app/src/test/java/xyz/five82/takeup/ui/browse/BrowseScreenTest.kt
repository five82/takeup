package xyz.five82.takeup.ui.browse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import xyz.five82.takeup.api.Collection
import xyz.five82.takeup.api.Item

class BrowseScreenTest {

    private fun item(id: Long, backdropImageId: Long = 0) = Item(id = id, title = "Item $id", backdropImageId = backdropImageId)

    @Test
    fun secondCollectionSkipsAnAlreadyClaimedFirstMember() {
        // Spielberg and Indiana Jones both open with Raiders: the second
        // collection to claim that backdrop must fall through to its own
        // next member instead of repeating it.
        val raiders = item(1, backdropImageId = 100)
        val collections = listOf(
            Collection("spielberg", "Spielberg", listOf(raiders, item(2, backdropImageId = 200))),
            Collection("indy", "Indiana Jones", listOf(raiders, item(3, backdropImageId = 300))),
        )

        val faces = collectionFaces(collections)

        assertEquals(100L, faces.getValue("spielberg")?.backdropImageId)
        assertEquals(300L, faces.getValue("indy")?.backdropImageId)
    }

    @Test
    fun fallsBackToFirstMemberWhenAllBackdropsAreClaimed() {
        val shared = item(1, backdropImageId = 100)
        val collections = listOf(
            Collection("first", "First", listOf(shared)),
            // Every member's backdrop (just the one, id 100) is already
            // claimed by "first", so "second" falls back to reusing it
            // rather than showing no face at all.
            Collection("second", "Second", listOf(shared, item(2, backdropImageId = 0))),
        )

        val faces = collectionFaces(collections)

        assertEquals(100L, faces.getValue("first")?.backdropImageId)
        assertEquals(100L, faces.getValue("second")?.backdropImageId)
    }

    @Test
    fun membersWithNoBackdropAreSkipped() {
        val collections = listOf(
            Collection(
                "gapped",
                "Gapped",
                listOf(item(1, backdropImageId = 0), item(2, backdropImageId = 0), item(3, backdropImageId = 300)),
            ),
        )

        val faces = collectionFaces(collections)

        assertEquals(300L, faces.getValue("gapped")?.backdropImageId)
    }

    @Test
    fun noMemberWithABackdropLeavesTheCollectionFaceless() {
        val collections = listOf(
            Collection("blank", "Blank", listOf(item(1, backdropImageId = 0), item(2, backdropImageId = 0))),
        )

        val faces = collectionFaces(collections)

        assertNull(faces.getValue("blank"))
    }
}
