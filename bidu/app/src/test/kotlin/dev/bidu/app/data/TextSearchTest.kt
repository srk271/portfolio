package dev.bidu.app.data

import dev.bidu.app.data.room.LikePattern
import dev.bidu.app.data.room.TextSearch
import org.junit.Assert.assertEquals
import org.junit.Test

class TextSearchTest {
    @Test fun fillerWordsAreDropped() {
        assertEquals(listOf("gym"), TextSearch.tokens("gym wala note"))
        assertEquals(listOf("doodh"), TextSearch.tokens("doodh ka reminder"))
    }

    @Test fun scoreCountsMatchedTokens() {
        assertEquals(1, TextSearch.score("Kal gym jaana hai", listOf("gym", "yoga")))
        assertEquals(0, TextSearch.score("Kal office", listOf("gym")))
    }

    @Test fun likeWildcardsAreEscaped() {
        assertEquals("50\\% off\\_now", LikePattern.escape("50% off_now"))
    }
}
