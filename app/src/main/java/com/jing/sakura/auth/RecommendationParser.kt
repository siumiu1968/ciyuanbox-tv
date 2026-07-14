package com.jing.sakura.auth

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.jing.sakura.data.AnimeData
import com.jing.sakura.repo.CycaniSource
import java.net.URLDecoder

object RecommendationParser {
    fun parse(body: String): List<AnimeData> {
        val root = JsonParser.parseString(body).asJsonObject
        val recommendations = root.getAsJsonArray("recommendations") ?: return emptyList()
        return recommendations.mapNotNull { element ->
            val item = element.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@mapNotNull null
            val id = item.string("id").ifBlank { item.string("vod_id") }
            val title = item.string("title").ifBlank {
                item.string("name").ifBlank { item.string("vod_name") }
            }
            if (id.isBlank() || title.isBlank()) return@mapNotNull null
            val currentEpisode = item.string("currentEpisode").ifBlank {
                item.string("status").ifBlank {
                    item.string("remarks").ifBlank { item.string("vod_remarks") }
                }
            }
            val imageUrl = normalizePoster(item.string("poster").ifBlank {
                item.string("pic").ifBlank { item.string("vod_pic") }
            })
            val description = item.string("summary").ifBlank {
                item.string("blurb").ifBlank { item.string("vod_content") }
            }
            AnimeData(
                id = id,
                url = "$CYCANI_API_BASE/video/info/$id",
                title = title,
                currentEpisode = currentEpisode,
                imageUrl = imageUrl,
                description = description,
                tags = item.tags(),
                sourceId = CycaniSource.SOURCE_ID
            )
        }.distinctBy(AnimeData::id)
    }

    private fun JsonObject.tags(): String {
        val tags = get("tags") ?: get("class") ?: get("vod_class") ?: return ""
        return if (tags.isJsonArray) {
            tags.asJsonArray.mapNotNull { it.takeUnless(JsonElement::isJsonNull)?.asString }.joinToString("、")
        } else {
            tags.asString
        }
    }

    private fun JsonObject.string(key: String): String =
        get(key)?.takeUnless(JsonElement::isJsonNull)?.asString.orEmpty()

    private fun normalizePoster(value: String): String {
        val proxyMarker = "/anime/api/image?url="
        val markerIndex = value.indexOf(proxyMarker)
        if (markerIndex < 0) return value
        val encoded = value.substring(markerIndex + proxyMarker.length).substringBefore('&')
        return runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrDefault(value)
    }

    private const val CYCANI_API_BASE = "https://pc.cycback.org"
}
