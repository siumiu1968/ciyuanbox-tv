package com.jing.sakura.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePlaybackCommandParserTest {
    @Test
    fun parsesExactResumePosition() {
        val command = RemotePlaybackCommandParser.parse(
            """
                {"command":{
                  "id":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "type":"open_anime",
                  "animeId":"12723",
                  "sourceId":"cycani",
                  "episodeIndex":3,
                  "currentTimeSeconds":125.75
                }}
            """.trimIndent()
        ) as RemotePlaybackCommand.OpenAnime

        assertEquals("12723", command.animeId)
        assertEquals(3, command.episodeIndex)
        assertEquals(125.75, command.currentTimeSeconds ?: -1.0, 0.0)
    }

    @Test
    fun remainsCompatibleWithCommandsWithoutPosition() {
        val command = RemotePlaybackCommandParser.parse(
            """
                {"command":{
                  "id":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                  "type":"open_anime",
                  "animeId":"12723",
                  "sourceId":"cycani",
                  "episodeIndex":0
                }}
            """.trimIndent()
        ) as RemotePlaybackCommand.OpenAnime

        assertNull(command.currentTimeSeconds)
    }

    @Test
    fun rejectsNegativeResumePosition() {
        val command = RemotePlaybackCommandParser.parse(
            """
                {"command":{
                  "id":"cccccccccccccccccccccccccccccccc",
                  "type":"open_anime",
                  "animeId":"12723",
                  "sourceId":"cycani",
                  "episodeIndex":0,
                  "currentTimeSeconds":-1
                }}
            """.trimIndent()
        )

        assertTrue(command is RemotePlaybackCommand.Invalid)
    }
}
