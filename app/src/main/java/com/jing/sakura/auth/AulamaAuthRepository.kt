package com.jing.sakura.auth

import android.os.Build
import com.google.gson.JsonObject
import com.jing.sakura.BuildConfig
import com.jing.sakura.data.AnimeData
import com.jing.sakura.extend.executeWithCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AulamaAuthRepository(
    private val client: OkHttpClient,
    private val storage: SecureAuthStorage
) {
    private val _session = MutableStateFlow(
        storage.loadSession()?.takeUnless(AuthSession::isExpired)
    )
    val session: StateFlow<AuthSession?> = _session

    suspend fun requestDeviceCode(nowEpochMs: Long = System.currentTimeMillis()): DeviceCodeRequestResult {
        val body = JsonObject().apply {
            addProperty("deviceId", storage.stableDeviceId())
            addProperty("deviceName", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            addProperty("appVersion", BuildConfig.VERSION_NAME)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        return execute(
            Request.Builder().url("$API_BASE/device/code").post(body).build()
        ) { code, responseBody, retryAfter ->
            DeviceAuthParser.parseDeviceCode(code, responseBody, retryAfter, nowEpochMs)
        }
    }

    suspend fun pollToken(
        deviceCode: String,
        nowEpochMs: Long = System.currentTimeMillis()
    ): DeviceTokenPollResult {
        val body = JsonObject().apply {
            addProperty("device_code", deviceCode)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        return execute(
            Request.Builder().url("$API_BASE/device/token").post(body).build()
        ) { code, responseBody, retryAfter ->
            DeviceAuthParser.parseTokenPoll(code, responseBody, retryAfter, nowEpochMs)
        }
    }

    fun authorize(result: DeviceTokenPollResult.Authorized, nowEpochMs: Long) {
        val session = AuthSession(
            accessToken = result.accessToken,
            tokenType = result.tokenType,
            expiresAtEpochMs = nowEpochMs + result.expiresInSeconds * 1000L,
            account = result.account
        )
        storage.saveSession(session)
        _session.value = session
    }

    suspend fun validateAccount(session: AuthSession): AccountValidationResult = runCatching {
        val request = authenticatedRequest("$API_BASE/device/me", session).get().build()
        client.executeWithCoroutine(request).use { response ->
            when (response.code) {
                200 -> {
                    val account = DeviceAuthParser.parseAccountPayload(response.body?.string().orEmpty())
                    val updated = session.copy(account = account)
                    storage.saveSession(updated)
                    _session.value = updated
                    AccountValidationResult.Valid(account)
                }
                401, 403 -> AccountValidationResult.Unauthorized
                else -> AccountValidationResult.Unavailable
            }
        }
    }.getOrDefault(AccountValidationResult.Unavailable)

    suspend fun fetchRecommendations(): List<AnimeData> {
        val session = _session.value ?: return emptyList()
        val request = authenticatedRequest("$API_BASE/home", session).get().build()
        return client.executeWithCoroutine(request).use { response ->
            if (response.code == 401 || response.code == 403) {
                clearSession()
                return@use emptyList()
            }
            if (!response.isSuccessful) throw IllegalStateException("推薦請求失敗（${response.code}）")
            RecommendationParser.parse(response.body?.string().orEmpty())
        }
    }

    suspend fun logout() {
        val existing = _session.value
        try {
            if (existing != null) {
                val request = authenticatedRequest("$API_BASE/device/session", existing).delete().build()
                runCatching { client.executeWithCoroutine(request).close() }
            }
        } finally {
            clearSession()
        }
    }

    fun clearSession() {
        storage.clearSession()
        _session.value = null
    }

    private fun authenticatedRequest(url: String, session: AuthSession): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Authorization", "${session.tokenType} ${session.accessToken}")
            .header("Accept", "application/json")

    private suspend fun <T> execute(request: Request, parser: (Int, String, String?) -> T): T =
        client.executeWithCoroutine(request).use { response ->
            parser(
                response.code,
                response.body?.string().orEmpty(),
                response.header("Retry-After")
            )
        }

    companion object {
        private const val API_BASE = "https://aulama.org/anime/api"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
