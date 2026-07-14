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
        return parseItems(recommendations)
    }

    internal fun parseItems(items: Iterable<JsonElement>): List<AnimeData> =
        items.mapNotNull { element ->
            val item = element.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@mapNotNull null
            mapItem(item)
        }.distinctBy(AnimeData::id)

    internal fun mapItem(
        item: JsonObject,
        idKeys: List<String> = listOf("id", "animeId", "vod_id"),
        titleKeys: List<String> = listOf("title", "animeTitle", "name", "vod_name"),
        episodeKeys: List<String> = listOf(
            "currentEpisode", "episodeLabel", "subtitle", "status", "remarks", "vod_remarks"
        )
    ): AnimeData? {
        val id = idKeys.firstNotNullOfOrNull { item.string(it).takeIf(String::isNotBlank) }.orEmpty()
        val title = titleKeys.firstNotNullOfOrNull { item.string(it).takeIf(String::isNotBlank) }.orEmpty()
        if (id.isBlank() || title.isBlank()) return null
        val currentEpisode = episodeKeys
            .firstNotNullOfOrNull { item.string(it).takeIf(String::isNotBlank) }
            .orEmpty()
        val imageUrl = normalizePoster(
            listOf("poster", "pic", "vod_pic")
                .firstNotNullOfOrNull { item.string(it).takeIf(String::isNotBlank) }
                .orEmpty()
        )
        val description = listOf(
            "summary", "description", "synopsis", "blurb", "vod_blurb", "vod_content", "content"
        )
            .firstNotNullOfOrNull { item.string(it).takeIf(String::isNotBlank) }
            .orEmpty()
        return AnimeData(
            id = id,
            url = "$CYCANI_API_BASE/video/info/$id",
            title = title,
            currentEpisode = currentEpisode,
            imageUrl = imageUrl,
            description = description,
            tags = item.tags(),
            sourceId = CycaniSource.SOURCE_ID,
            year = listOf("year", "releaseYear", "release_year", "vod_year")
                .firstNotNullOfOrNull { item.string(it).takeIf(String::isNotBlank) }
                .orEmpty()
        )
    }

    internal fun JsonObject.tags(): String {
        val tags = get("tags") ?: get("class") ?: get("vod_class") ?: return ""
        return if (tags.isJsonArray) {
            tags.asJsonArray.mapNotNull { it.takeUnless(JsonElement::isJsonNull)?.asString }.joinToString("、")
        } else {
            tags.asString
        }
    }

    internal fun JsonObject.string(key: String): String =
        get(key)?.takeUnless(JsonElement::isJsonNull)?.asString.orEmpty()

    internal fun normalizePoster(value: String): String {
        val proxyMarker = "/anime/api/image?url="
        val markerIndex = value.indexOf(proxyMarker)
        if (markerIndex < 0) return value
        val encoded = value.substring(markerIndex + proxyMarker.length).substringBefore('&')
        return runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrDefault(value)
    }

    private const val CYCANI_API_BASE = "https://pc.cycback.org"
}
