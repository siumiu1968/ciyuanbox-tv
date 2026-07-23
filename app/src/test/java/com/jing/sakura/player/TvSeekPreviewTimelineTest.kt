package com.jing.sakura.player

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvSeekPreviewTimelineTest {
    @Test
    fun emptyDurationHasNoSeekPositions() {
        assertArrayEquals(longArrayOf(), TvSeekPreviewTimeline.positions(0L))
    }

    @Test
    fun shortEpisodeUsesFiveSecondBucketsAndIncludesEnd() {
        val positions = TvSeekPreviewTimeline.positions(12_300L)
        assertArrayEquals(longArrayOf(0L, 5_000L, 10_000L, 12_300L), positions)
    }

    @Test
    fun longVideoKeepsThumbnailCountBounded() {
        val positions = TvSeekPreviewTimeline.positions(3L * 60L * 60L * 1000L)
        assertTrue(positions.size <= 122)
        assertEquals(3L * 60L * 60L * 1000L, positions.last())
    }

    @Test
    fun fourByThreeFrameIsPillarboxed() {
        assertEquals(
            TvSeekPreviewFrameLayout(width = 240, height = 180, left = 40, top = 0),
            TvSeekPreviewFrame.layout(4f / 3f)
        )
    }

    @Test
    fun sixteenByNineFrameUsesFullCanvas() {
        assertEquals(
            TvSeekPreviewFrameLayout(width = 320, height = 180, left = 0, top = 0),
            TvSeekPreviewFrame.layout(16f / 9f)
        )
    }

    @Test
    fun cinemaFrameIsLetterboxed() {
        val layout = TvSeekPreviewFrame.layout(2.35f)
        assertEquals(320, layout.width)
        assertTrue(layout.height < 180)
        assertTrue(layout.top > 0)
    }
}
