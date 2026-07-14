package com.jing.sakura.auth

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object DeviceAuthParser {
    fun parseDeviceCode(
        statusCode: Int,
        body: String,
        retryAfter: String?,
        nowEpochMs: Long
    ): DeviceCodeRequestResult {
        if (statusCode == 429) {
            return DeviceCodeRequestResult.RateLimited(
                parseRetryAfter(retryAfter ?: bodyRetryAfter(body), nowEpochMs)
            )
        }
        if (statusCode !in 200..299) {
            return DeviceCodeRequestResult.Failed(errorMessage(body, statusCode))
        }
        return runCatching {
            val root = JsonParser.parseString(body).asJsonObject
            val expiresIn = root.long("expires_in").takeIf { it > 0 } ?: 120L
            DeviceCodeRequestResult.Ready(
                DeviceCode(
                    deviceCode = root.string("device_code").required("device_code"),
                    userCode = root.string("user_code").required("user_code"),
                    verificationUri = root.string("verification_uri").required("verification_uri"),
                    expiresAtEpochMs = nowEpochMs + expiresIn * 1000L,
                    intervalSeconds = root.long("interval").coerceAtLeast(1L)
                )
            )
        }.getOrElse { DeviceCodeRequestResult.Failed("登入代碼格式不正確") }
    }

    fun parseTokenPoll(
        statusCode: Int,
        body: String,
        retryAfter: String?,
        nowEpochMs: Long
    ): DeviceTokenPollResult = when (statusCode) {
        202 -> DeviceTokenPollResult.Pending
        410 -> DeviceTokenPollResult.Expired
        429 -> DeviceTokenPollResult.RateLimited(
            parseRetryAfter(retryAfter ?: bodyRetryAfter(body), nowEpochMs)
        )
        200 -> runCatching {
            val root = JsonParser.parseString(body).asJsonObject
            DeviceTokenPollResult.Authorized(
                accessToken = root.string("access_token").required("access_token"),
                tokenType = root.string("token_type").ifBlank { "Bearer" },
                expiresInSeconds = root.long("expires_in").coerceAtLeast(1L),
                account = parseAccount(root.get("account"))
            )
        }.getOrElse { DeviceTokenPollResult.Failed("登入回應格式不正確") }
        else -> DeviceTokenPollResult.Failed(errorMessage(body, statusCode))
    }

    fun parseAccountPayload(body: String): AulamaAccount {
        val root = JsonParser.parseString(body).asJsonObject
        return parseAccount(root.get("account") ?: root)
    }

    internal fun parseRetryAfter(value: String?, nowEpochMs: Long): Long {
        value?.trim()?.toLongOrNull()?.let { return it.coerceAtLeast(1L) }
        val date = runCatching {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("GMT")
            }.parse(value.orEmpty())?.time
        }.getOrNull()
        return date?.let { ceilSeconds(it - nowEpochMs) } ?: 60L
    }

    private fun parseAccount(element: JsonElement?): AulamaAccount {
        val account = element?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
        val name = listOf("name", "display_name", "displayName", "username", "email")
            .firstNotNullOfOrNull { key -> account.string(key).takeIf(String::isNotBlank) }
            ?: "Aulama 帳戶"
        return AulamaAccount(
            name = name,
            role = account.string("role").ifBlank {
                if (account.get("is_admin")?.asBoolean == true) "管理員" else "會員"
            }
        )
    }

    private fun errorMessage(body: String, statusCode: Int): String = runCatching {
        val root = JsonParser.parseString(body).asJsonObject
        root.string("error").ifBlank { root.string("message") }
    }.getOrDefault("").ifBlank { "請求失敗（$statusCode）" }

    private fun bodyRetryAfter(body: String): String? = runCatching {
        JsonParser.parseString(body).asJsonObject.get("retry_after")?.asString
    }.getOrNull()

    private fun JsonObject.string(key: String): String =
        get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()

    private fun JsonObject.long(key: String): Long =
        get(key)?.takeUnless { it.isJsonNull }?.asLong ?: 0L

    private fun String.required(field: String): String =
        ifBlank { throw IllegalArgumentException("Missing $field") }

    private fun ceilSeconds(milliseconds: Long): Long =
        ((milliseconds.coerceAtLeast(0L) + 999L) / 1000L).coerceAtLeast(1L)
}
