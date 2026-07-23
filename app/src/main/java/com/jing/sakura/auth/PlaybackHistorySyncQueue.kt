package com.jing.sakura.auth

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.MessageDigest

data class QueuedPlaybackHistory(
    val accountFingerprint: String,
    val payload: PlaybackHistoryPayload
)

class PlaybackHistorySyncQueue(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE
    )
    private val gson = Gson()
    private val queueType = object : TypeToken<LinkedHashMap<String, QueuedPlaybackHistory>>() {}.type

    @Synchronized
    fun enqueue(accountKey: String, payload: PlaybackHistoryPayload) {
        val fingerprint = fingerprint(accountKey)
        if (fingerprint.isBlank()) return
        val queue = readQueue()
        queue[entryKey(fingerprint, payload)] = QueuedPlaybackHistory(fingerprint, payload)
        val retained = queue.values
            .sortedByDescending { it.payload.updatedAt }
            .take(MAX_PENDING_ITEMS)
            .associateByTo(linkedMapOf()) { entryKey(it.accountFingerprint, it.payload) }
        writeQueue(retained)
    }

    @Synchronized
    fun pendingForAccount(accountKey: String): List<QueuedPlaybackHistory> {
        val fingerprint = fingerprint(accountKey)
        if (fingerprint.isBlank()) return emptyList()
        return readQueue().values
            .filter { it.accountFingerprint == fingerprint }
            .sortedBy { it.payload.updatedAt }
    }

    @Synchronized
    fun removeIfCurrent(accountKey: String, payload: PlaybackHistoryPayload) {
        val fingerprint = fingerprint(accountKey)
        if (fingerprint.isBlank()) return
        val queue = readQueue()
        val key = entryKey(fingerprint, payload)
        if (queue[key]?.payload?.updatedAt != payload.updatedAt) return
        queue.remove(key)
        writeQueue(queue)
    }

    private fun readQueue(): LinkedHashMap<String, QueuedPlaybackHistory> {
        val raw = preferences.getString(KEY_QUEUE, null) ?: return linkedMapOf()
        return runCatching {
            gson.fromJson<LinkedHashMap<String, QueuedPlaybackHistory>>(raw, queueType)
                ?: linkedMapOf()
        }.getOrElse { linkedMapOf() }
    }

    private fun writeQueue(queue: Map<String, QueuedPlaybackHistory>) {
        preferences.edit().putString(KEY_QUEUE, gson.toJson(queue)).commit()
    }

    private fun fingerprint(accountKey: String): String {
        val normalized = accountKey.trim().lowercase()
        if (normalized.isBlank()) return ""
        return MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun entryKey(
        accountFingerprint: String,
        payload: PlaybackHistoryPayload
    ): String = "$accountFingerprint:${payload.sourceTypeId}:${payload.animeId}"

    companion object {
        private const val PREFERENCES = "aulama_playback_history_sync"
        private const val KEY_QUEUE = "pending_history"
        private const val MAX_PENDING_ITEMS = 200
    }
}
