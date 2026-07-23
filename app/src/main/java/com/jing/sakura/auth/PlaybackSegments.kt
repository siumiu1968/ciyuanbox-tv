package com.jing.sakura.auth

import com.google.gson.JsonParser

data class PlaybackSegments(
    val introStartMs: Long? = null,
    val introEndMs: Long? = null,
    val outroStartMs: Long? = null,
    val outroEndMs: Long? = null,
    val outroAction: String = "skip"
)

object PlaybackSegmentsParser {
    fun parse(body: String): PlaybackSegments? {
        return runCatching {
            val item =
            JsonParser.parseString(body).asJsonObject.getAsJsonObject("item")
            PlaybackSegments(
                introStartMs = secondsToMs(item.get("introStart")?.takeUnless { it.isJsonNull }?.asDouble),
                introEndMs = secondsToMs(item.get("introEnd")?.takeUnless { it.isJsonNull }?.asDouble),
                outroStartMs = secondsToMs(item.get("outroStart")?.takeUnless { it.isJsonNull }?.asDouble),
                outroEndMs = secondsToMs(item.get("outroEnd")?.takeUnless { it.isJsonNull }?.asDouble),
                outroAction = item.get("outroAction")?.takeUnless { it.isJsonNull }?.asString
                    ?.takeIf { it == "next" }
                    ?: "skip"
            )
        }.getOrNull()
    }

    private fun secondsToMs(seconds: Double?): Long? = seconds
        ?.takeIf { it.isFinite() && it >= 0.0 }
        ?.let { (it * 1000.0).toLong() }
}
