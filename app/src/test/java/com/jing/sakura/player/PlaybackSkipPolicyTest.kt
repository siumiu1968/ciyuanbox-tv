package com.jing.sakura.player

import com.jing.sakura.auth.PlaybackSegments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSkipPolicyTest {
    @Test
    fun noCreditOutroAdvancesToNextEpisode() {
        val active = PlaybackSkipPolicy.activeSkip(
            segments = PlaybackSegments(
                outroStartMs = 1_200_000L,
                outroEndMs = 1_440_000L,
                outroAction = "next"
            ),
            positionMs = 1_250_000L,
            hasNextEpisode = true
        )
        assertEquals(ActivePlaybackSkip.Type.OUTRO, active?.type)
        assertTrue(active?.advancesEpisode == true)
    }

    @Test
    fun postCreditOutroOnlySeeksPastTheEnding() {
        val active = PlaybackSkipPolicy.activeSkip(
            segments = PlaybackSegments(
                outroStartMs = 1_200_000L,
                outroEndMs = 1_350_000L,
                outroAction = "skip"
            ),
            positionMs = 1_250_000L,
            hasNextEpisode = true
        )
        assertFalse(active?.advancesEpisode == true)
        assertEquals(1_350_000L, active?.targetMs)
    }

    @Test
    fun finalSecondAdvancesOnlyWhenNextEpisodeExists() {
        assertTrue(PlaybackSkipPolicy.shouldAutoAdvanceAtEnd(99_000L, 100_000L, true))
        assertFalse(PlaybackSkipPolicy.shouldAutoAdvanceAtEnd(98_999L, 100_000L, true))
        assertFalse(PlaybackSkipPolicy.shouldAutoAdvanceAtEnd(100_000L, 100_000L, false))
    }
}
