package com.jing.sakura.auth

import kotlin.math.ceil

data class AulamaAccount(
    val name: String,
    val role: String
)

data class AuthSession(
    val accessToken: String,
    val tokenType: String,
    val expiresAtEpochMs: Long,
    val account: AulamaAccount
) {
    fun isExpired(nowEpochMs: Long = System.currentTimeMillis()): Boolean =
        expiresAtEpochMs <= nowEpochMs
}

data class DeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresAtEpochMs: Long,
    val intervalSeconds: Long
) {
    fun remainingSeconds(nowEpochMs: Long = System.currentTimeMillis()): Long =
        ceil((expiresAtEpochMs - nowEpochMs).coerceAtLeast(0L) / 1000.0).toLong()

    fun isExpired(nowEpochMs: Long = System.currentTimeMillis()): Boolean =
        remainingSeconds(nowEpochMs) == 0L
}

sealed interface DeviceCodeRequestResult {
    data class Ready(val code: DeviceCode) : DeviceCodeRequestResult
    data class RateLimited(val retryAfterSeconds: Long) : DeviceCodeRequestResult
    data class Failed(val message: String) : DeviceCodeRequestResult
}

sealed interface DeviceTokenPollResult {
    data object Pending : DeviceTokenPollResult
    data object Expired : DeviceTokenPollResult
    data class RateLimited(val retryAfterSeconds: Long) : DeviceTokenPollResult
    data class Authorized(
        val accessToken: String,
        val tokenType: String,
        val expiresInSeconds: Long,
        val account: AulamaAccount
    ) : DeviceTokenPollResult
    data class Failed(val message: String) : DeviceTokenPollResult
}

sealed interface AccountValidationResult {
    data class Valid(val account: AulamaAccount) : AccountValidationResult
    data object Unauthorized : AccountValidationResult
    data object Unavailable : AccountValidationResult
}

sealed interface AuthUiState {
    data object Checking : AuthUiState
    data object RequestingCode : AuthUiState
    data class Waiting(
        val code: DeviceCode,
        val remainingSeconds: Long,
        val pending: Boolean = false
    ) : AuthUiState
    data class RateLimited(
        val code: DeviceCode?,
        val remainingSeconds: Long,
        val retryAfterSeconds: Long
    ) : AuthUiState
    data class Expired(val code: DeviceCode) : AuthUiState
    data class Error(val message: String) : AuthUiState
    data class Authenticated(val account: AulamaAccount) : AuthUiState
}
