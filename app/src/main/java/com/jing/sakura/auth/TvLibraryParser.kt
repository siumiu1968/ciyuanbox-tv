package com.jing.sakura.auth

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.jing.sakura.data.AnimeData

object TvLibraryParser {
    fun parseHome(body: String, weekday: Int): TvHomePayload {
        val root = JsonParser.parseString(body).asJsonObject
        val recommendations = RecommendationParser.parseItems(root.array("recommendations"))
        val theaterItems = RecommendationParser.parseItems(root.array("theaterItems"))
        val todayItems = root.array("days")
            .mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }
            .firstOrNull { it.get("day")?.asInt == weekday }
            ?.let { RecommendationParser.parseItems(it.array("items")) }
            .orEmpty()
        return TvHomePayload(
            recommendations = recommendations,
            todayUpdates = todayItems,
            theaterItems = theaterItems
        )
    }

    fun parseFavorites(body: String): List<AnimeData> {
        val root = JsonParser.parseString(body).asJsonObject
        return RecommendationParser.parseItems(root.array("items"))
    }

    fun parseHistory(body: String): List<AnimeData> {
        return parseHistoryItems(body).map(TvHistoryItem::anime)
    }

    fun parseHistoryItems(body: String): List<TvHistoryItem> {
        val root = runCatching { JsonParser.parseString(body) }
            .getOrNull()
            ?.takeIf(JsonElement::isJsonObject)
            ?.asJsonObject
            ?: return emptyList()
        return root.array("items").mapNotNull { element ->
            val item = element.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@mapNotNull null
            val anime = runCatching {
                RecommendationParser.mapItem(
                    item = item,
                    idKeys = listOf("animeId", "id"),
                    titleKeys = listOf("title", "animeTitle"),
                    episodeKeys = listOf("episodeLabel", "currentEpisode", "subtitle")
                )
            }.getOrNull() ?: return@mapNotNull null
            TvHistoryItem(
                anime = anime,
                episodeId = item.primitiveString("episodeId"),
                episodeLabel = item.primitiveString("episodeLabel"),
                episodeIndex = item.nonNegativeInt("episodeIndex"),
                episodeCount = item.nonNegativeInt("episodeCount"),
                currentTimeSeconds = item.nonNegativeDouble("currentTime"),
                durationSeconds = item.nonNegativeDouble("duration"),
                completed = item.boolean("completed"),
                sourceTypeId = item.primitiveString("sourceTypeId"),
                updatedAt = item.primitiveString("updatedAt")
            )
        }.distinctBy { it.anime.id }
    }

    private fun com.google.gson.JsonObject.array(key: String): JsonArray =
        get(key)?.takeIf(JsonElement::isJsonArray)?.asJsonArray ?: JsonArray()

    private fun JsonObject.primitiveString(key: String): String =
        get(key)
            ?.takeIf(JsonElement::isJsonPrimitive)
            ?.asJsonPrimitive
            ?.asString
            .orEmpty()

    private fun JsonObject.nonNegativeDouble(key: String): Double =
        primitiveString(key)
            .toDoubleOrNull()
            ?.takeIf(Double::isFinite)
            ?.coerceAtLeast(0.0)
            ?: 0.0

    private fun JsonObject.nonNegativeInt(key: String): Int {
        val value = primitiveString(key).toDoubleOrNull() ?: return 0
        if (!value.isFinite() || value < 0.0 || value > Int.MAX_VALUE) return 0
        return value.toInt()
    }

    private fun JsonObject.boolean(key: String): Boolean =
        when (primitiveString(key).trim().lowercase()) {
            "true", "1" -> true
            else -> false
        }
}
