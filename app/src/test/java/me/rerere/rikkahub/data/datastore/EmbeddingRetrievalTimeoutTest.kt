package me.rerere.rikkahub.data.datastore

import org.junit.Assert.assertEquals
import org.junit.Test

class EmbeddingRetrievalTimeoutTest {
    @Test
    fun getEmbeddingRetrievalTimeoutSeconds_defaultsTo2() {
        assertEquals(2, Settings().getEmbeddingRetrievalTimeoutSeconds())
    }

    @Test
    fun getEmbeddingRetrievalTimeoutSeconds_coercesNonPositiveTo1() {
        assertEquals(
            1,
            Settings(displaySetting = DisplaySetting(embeddingRetrievalTimeoutSeconds = 0)).getEmbeddingRetrievalTimeoutSeconds()
        )
        assertEquals(
            1,
            Settings(displaySetting = DisplaySetting(embeddingRetrievalTimeoutSeconds = -10)).getEmbeddingRetrievalTimeoutSeconds()
        )
    }

    @Test
    fun displaySetting_useLastTurnMemoryOnSkip_defaultsToTrue() {
        assertEquals(true, DisplaySetting().useLastTurnMemoryOnSkip)
    }
}

