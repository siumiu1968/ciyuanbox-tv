package com.jing.sakura.auth

import com.jing.sakura.player.ActivePlaybackSkip
import com.jing.sakura.player.PlaybackSkipPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSegmentsParserTest {
    @Test
    fun parsesBackendSecondsAndSafeNextAction() {
        val segments = PlaybackSegmentsParser.parse(
            """{"ok":true,"item":{"introStart":8.5,"introEnd":96,"outroStart":1320,"outroEnd":1430,"outroAction":"next"}}"""
        )

        assertEquals(8_500L, segments?.introStartMs)
        assertEquals(96_000L, segments?.introEndMs)
        val intro = PlaybackSkipPolicy.activeSkip(segments, 20_000L, hasNextEpisode = true)
        assertEquals(ActivePlaybackSkip.Type.INTRO, intro?.type)
        assertFalse(intro?.advancesEpisode ?: true)
        val outro = PlaybackSkipPolicy.activeSkip(segments, 1_350_000L, hasNextEpisode = true)
        assertTrue(outro?.advancesEpisode == true)
    }

    @Test
    fun rejectsInvalidRangesAndNeverAdvancesWithoutNextEpisode() {
        val invalid = PlaybackSegments(introStartMs = 90_000L, introEndMs = 10_000L)
        assertNull(PlaybackSkipPolicy.activeSkip(invalid, 30_000L, hasNextEpisode = true))

        val outro = PlaybackSegments(
            outroStartMs = 1_000L,
            outroEndMs = 2_000L,
            outroAction = "next"
        )
        assertFalse(
            PlaybackSkipPolicy.activeSkip(outro, 1_500L, hasNextEpisode = false)
                ?.advancesEpisode
                ?: true
        )
    }

    @Test
    fun rejectsMalformedBackendPayloadWithoutCrashingPlayer() {
        assertNull(
            PlaybackSegmentsParser.parse(
                """{"ok":true,"item":{"introStart":"not-a-number"}}"""
            )
        )
        assertNull(PlaybackSegmentsParser.parse("not-json"))
    }
}
