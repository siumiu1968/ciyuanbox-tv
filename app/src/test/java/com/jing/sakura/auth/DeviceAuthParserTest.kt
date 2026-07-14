package com.jing.sakura.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceAuthParserTest {
    @Test
    fun deviceCodeExpiresAtBackendDeadline() {
        val result = DeviceAuthParser.parseDeviceCode(
            statusCode = 200,
            body = """{"device_code":"device","user_code":"ABCD-EFGH","verification_uri":"https://aulama.org/device","expires_in":120,"interval":5}""",
            retryAfter = null,
            nowEpochMs = 1_000L
        ) as DeviceCodeRequestResult.Ready

        assertEquals(120L, result.code.remainingSeconds(1_000L))
        assertEquals(1L, result.code.remainingSeconds(120_001L))
        assertTrue(result.code.isExpired(121_000L))
    }

    @Test
    fun mapsPendingExpiredAndRateLimitedStatuses() {
        assertTrue(DeviceAuthParser.parseTokenPoll(202, "", null, 0) is DeviceTokenPollResult.Pending)
        assertTrue(DeviceAuthParser.parseTokenPoll(410, "", null, 0) is DeviceTokenPollResult.Expired)
        val limited = DeviceAuthParser.parseTokenPoll(429, "{\"retry_after\":37}", null, 0)
            as DeviceTokenPollResult.RateLimited
        assertEquals(37L, limited.retryAfterSeconds)
    }

    @Test
    fun parsesAuthorizedAccount() {
        val result = DeviceAuthParser.parseTokenPoll(
            statusCode = 200,
            body = """{"access_token":"secret","token_type":"Bearer","expires_in":3600,"account":{"name":"測試用戶","role":"會員"}}""",
            retryAfter = null,
            nowEpochMs = 0
        ) as DeviceTokenPollResult.Authorized

        assertEquals("secret", result.accessToken)
        assertEquals("測試用戶", result.account.name)
        assertEquals("會員", result.account.role)
    }
}
