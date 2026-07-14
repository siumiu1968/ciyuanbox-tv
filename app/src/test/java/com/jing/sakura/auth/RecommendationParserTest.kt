package com.jing.sakura.auth

import com.jing.sakura.repo.CycaniSource
import org.junit.Assert.assertEquals
import org.junit.Test

class RecommendationParserTest {
    @Test
    fun mapsHomeRecommendationsToCycaniAnimeData() {
        val items = RecommendationParser.parse(
            """
            {
              "ok": true,
              "recommendations": [
                {
                  "id": "42",
                  "title": "推薦動畫",
                  "poster": "/anime/api/image?url=https%3A%2F%2Fimg.example.com%2F42.jpg",
                  "currentEpisode": "更新至第 8 集",
                  "summary": "簡介",
                  "tags": ["奇幻", "冒險"]
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(1, items.size)
        assertEquals("42", items.single().id)
        assertEquals("推薦動畫", items.single().title)
        assertEquals("https://img.example.com/42.jpg", items.single().imageUrl)
        assertEquals("奇幻、冒險", items.single().tags)
        assertEquals(CycaniSource.SOURCE_ID, items.single().sourceId)
    }
}
