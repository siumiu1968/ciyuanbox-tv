package com.jing.sakura.remote

import com.google.gson.JsonParser

object RemotePlaybackCommandParser {
    private val commandIdPattern = Regex("^[a-fA-F0-9]{32}$")

    fun parse(body: String): RemotePlaybackCommand? {
        val root = JsonParser.parseString(body).asJsonObject
        val commandElement = root.get("command") ?: return null
        if (commandElement.isJsonNull) return null

        val command = commandElement.asJsonObject
        val id = command.string("id")
        if (!commandIdPattern.matches(id)) {
            throw IllegalArgumentException("Invalid remote command id")
        }
        if (command.string("type") != "open_anime") {
            return RemotePlaybackCommand.Invalid(id)
        }

        val animeId = command.string("animeId")
        val sourceId = command.string("sourceId")
        val episodeIndex = command.get("episodeIndex")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.asInt
        val currentTimeSeconds = command.get("currentTimeSeconds")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.asDouble
        if (animeId.isBlank() || sourceId.isBlank() || episodeIndex == null || episodeIndex < 0) {
            return RemotePlaybackCommand.Invalid(id)
        }
        if (currentTimeSeconds != null && (!currentTimeSeconds.isFinite() || currentTimeSeconds < 0.0)) {
            return RemotePlaybackCommand.Invalid(id)
        }
        return RemotePlaybackCommand.OpenAnime(
            id = id,
            animeId = animeId,
            sourceId = sourceId,
            episodeIndex = episodeIndex,
            currentTimeSeconds = currentTimeSeconds
        )
    }

    private fun com.google.gson.JsonObject.string(name: String): String =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString.orEmpty()
}
