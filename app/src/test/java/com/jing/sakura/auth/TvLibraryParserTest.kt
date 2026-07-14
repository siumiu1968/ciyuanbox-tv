package com.jing.sakura.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvLibraryParserTest {
    @Test
    fun mapsHomeRecommendationsTodayAndTheater() {
        val payload = TvLibraryParser.parseHome(
            body = """
                {
                  "recommendations": [{"id":"r1","title":"推薦一","poster":"r.jpg"}],
                  "days": [
                    {"day":1,"items":[]},
                    {"day":2,"items":[{"id":"d1","title":"今日一","poster":"d.jpg"}]}
                  ],
                  "theaterItems": [{"id":"t1","title":"劇場一","poster":"t.jpg"}]
                }
            """.trimIndent(),
            weekday = 2
        )

        assertEquals("推薦一", payload.recommendations.single().title)
        assertEquals("今日一", payload.todayUpdates.single().title)
        assertEquals("劇場一", payload.theaterItems.single().title)
    }

    @Test
    fun mapsFavoriteItems() {
        val items = TvLibraryParser.parseFavorites(
            """{"items":[{"id":"f1","title":"收藏一","subtitle":"第 3 集","poster":"f.jpg"}]}"""
        )

        assertEquals(1, items.size)
        assertEquals("收藏一", items.single().title)
        assertEquals("第 3 集", items.single().currentEpisode)
    }

    @Test
    fun mapsHistoryUsingAnimeFields() {
        val items = TvLibraryParser.parseHistory(
            """
                {"items":[{
                  "animeId":"h1",
                  "animeTitle":"續播一",
                  "episodeLabel":"第 8 集",
                  "poster":"/anime/api/image?url=https%3A%2F%2Fimg.example.com%2Fh.jpg",
                  "tags":["日常","校園"]
                }]}
            """.trimIndent()
        )

        assertEquals("h1", items.single().id)
        assertEquals("第 8 集", items.single().currentEpisode)
        assertEquals("日常、校園", items.single().tags)
        assertTrue(items.single().imageUrl.startsWith("https://img.example.com/"))
    }

    @Test
    fun mapsTypedHistoryProgressFromNumbersAndNumericStrings() {
        val item = TvLibraryParser.parseHistoryItems(
            """
                {"items":[{
                  "animeId":"h1",
                  "title":"續播一",
                  "poster":"h.jpg",
                  "episodeId":"ep-8",
                  "episodeLabel":"第 8 集",
                  "episodeIndex":"8",
                  "episodeCount":12.0,
                  "currentTime":"125.5",
                  "duration":1500,
                  "completed":"true",
                  "sourceTypeId":7,
                  "updatedAt":"2026-07-14T10:00:00Z"
                }]}
            """.trimIndent()
        ).single()

        assertEquals("h1", item.anime.id)
        assertEquals("續播一", item.anime.title)
        assertEquals("h.jpg", item.anime.imageUrl)
        assertEquals("ep-8", item.episodeId)
        assertEquals("第 8 集", item.episodeLabel)
        assertEquals(8, item.episodeIndex)
        assertEquals(12, item.episodeCount)
        assertEquals(125.5, item.currentTimeSeconds, 0.0)
        assertEquals(1500.0, item.durationSeconds, 0.0)
        assertTrue(item.completed)
        assertEquals("7", item.sourceTypeId)
        assertEquals("2026-07-14T10:00:00Z", item.updatedAt)
    }

    @Test
    fun toleratesMissingAndMalformedHistoryProgress() {
        val item = TvLibraryParser.parseHistoryItems(
            """
                {"items":[{
                  "animeId":"h1",
                  "title":"續播一",
                  "episodeIndex":"unknown",
                  "episodeCount":null,
                  "currentTime":"NaN",
                  "duration":{},
                  "completed":"unknown"
                }]}
            """.trimIndent()
        ).single()

        assertEquals("", item.episodeId)
        assertEquals(0, item.episodeIndex)
        assertEquals(0, item.episodeCount)
        assertEquals(0.0, item.currentTimeSeconds, 0.0)
        assertEquals(0.0, item.durationSeconds, 0.0)
        assertEquals(false, item.completed)
        assertEquals("", item.updatedAt)
    }

    @Test
    fun keepsFirstHistoryItemForDuplicateAnimeInServerOrder() {
        val items = TvLibraryParser.parseHistoryItems(
            """
                {"items":[
                  {"animeId":"h1","title":"續播一","episodeId":"new","currentTime":300},
                  {"animeId":"h2","title":"續播二","episodeId":"only","currentTime":200},
                  {"animeId":"h1","title":"續播一","episodeId":"old","currentTime":100}
                ]}
            """.trimIndent()
        )

        assertEquals(listOf("h1", "h2"), items.map { it.anime.id })
        assertEquals("new", items.first().episodeId)
        assertEquals(300.0, items.first().currentTimeSeconds, 0.0)
        assertEquals(items.map(TvHistoryItem::anime), TvLibraryParser.parseHistory(
            """
                {"items":[
                  {"animeId":"h1","title":"續播一","episodeId":"new","currentTime":300},
                  {"animeId":"h2","title":"續播二","episodeId":"only","currentTime":200},
                  {"animeId":"h1","title":"續播一","episodeId":"old","currentTime":100}
                ]}
            """.trimIndent()
        ))
    }
}
