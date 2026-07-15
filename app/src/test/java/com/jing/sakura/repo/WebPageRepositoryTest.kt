package com.jing.sakura.repo

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class WebPageRepositoryTest {
    private val repository = WebPageRepository(OkHttpClient())

    @Test
    fun keepsKnownRemotePlaybackSource() {
        assertEquals(CycaniSource.SOURCE_ID, repository.resolveAnimationSourceId(CycaniSource.SOURCE_ID))
    }

    @Test
    fun fallsBackToOnlyConfiguredSourceForLegacyRemoteCommand() {
        assertEquals(
            CycaniSource.SOURCE_ID,
            repository.resolveAnimationSourceId("legacy-encoded-episode-payload")
        )
    }
}
