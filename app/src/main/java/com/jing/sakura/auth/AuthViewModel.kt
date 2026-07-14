package com.jing.sakura.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: AulamaAuthRepository
) : ViewModel() {
    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Checking)
    val state: StateFlow<AuthUiState> = _state
    private var loginJob: Job? = null

    init {
        viewModelScope.launch {
            repository.session.drop(1).collect { session ->
                if (session == null && _state.value is AuthUiState.Authenticated) {
                    beginLogin()
                }
            }
        }
        bootstrap()
    }

    fun retryLogin() {
        beginLogin()
    }

    fun logout() {
        loginJob?.cancel()
        loginJob = viewModelScope.launch {
            _state.value = AuthUiState.Checking
            repository.logout()
            requestAndPoll()
        }
    }

    private fun bootstrap() {
        loginJob = viewModelScope.launch {
            val session = repository.session.value
            if (session == null || session.isExpired()) {
                repository.clearSession()
                requestAndPoll()
                return@launch
            }
            when (val validation = repository.validateAccount(session)) {
                is AccountValidationResult.Valid -> {
                    _state.value = AuthUiState.Authenticated(validation.account)
                }
                AccountValidationResult.Unauthorized -> {
                    repository.clearSession()
                    requestAndPoll()
                }
                AccountValidationResult.Unavailable -> {
                    _state.value = AuthUiState.Authenticated(session.account)
                }
            }
        }
    }

    private fun beginLogin() {
        loginJob?.cancel()
        loginJob = viewModelScope.launch { requestAndPoll() }
    }

    private suspend fun requestAndPoll() {
        _state.value = AuthUiState.RequestingCode
        when (val request = repository.requestDeviceCode()) {
            is DeviceCodeRequestResult.Ready -> poll(request.code)
            is DeviceCodeRequestResult.RateLimited -> {
                for (remaining in request.retryAfterSeconds downTo 1L) {
                    _state.value = AuthUiState.RateLimited(null, 0, remaining)
                    delay(1000L)
                }
                requestAndPoll()
            }
            is DeviceCodeRequestResult.Failed -> {
                _state.value = AuthUiState.Error(request.message)
            }
        }
    }

    private suspend fun poll(code: DeviceCode) {
        var nextPollAt = System.currentTimeMillis() + code.intervalSeconds * 1000L
        var pending = false
        while (true) {
            val now = System.currentTimeMillis()
            val remaining = code.remainingSeconds(now)
            if (remaining == 0L) {
                _state.value = AuthUiState.Expired(code)
                return
            }
            if (now < nextPollAt) {
                if (_state.value !is AuthUiState.RateLimited) {
                    _state.value = AuthUiState.Waiting(code, remaining, pending)
                } else {
                    val current = _state.value as AuthUiState.RateLimited
                    _state.value = current.copy(
                        remainingSeconds = remaining,
                        retryAfterSeconds = ((nextPollAt - now + 999L) / 1000L).coerceAtLeast(1L)
                    )
                }
                delay(minOf(1000L, nextPollAt - now))
                continue
            }

            when (val result = repository.pollToken(code.deviceCode, now)) {
                DeviceTokenPollResult.Pending -> {
                    pending = true
                    nextPollAt = now + code.intervalSeconds * 1000L
                    _state.value = AuthUiState.Waiting(code, remaining, pending = true)
                }
                DeviceTokenPollResult.Expired -> {
                    _state.value = AuthUiState.Expired(code)
                    return
                }
                is DeviceTokenPollResult.RateLimited -> {
                    nextPollAt = now + result.retryAfterSeconds * 1000L
                    _state.value = AuthUiState.RateLimited(code, remaining, result.retryAfterSeconds)
                }
                is DeviceTokenPollResult.Authorized -> {
                    repository.authorize(result, now)
                    _state.value = AuthUiState.Authenticated(result.account)
                    return
                }
                is DeviceTokenPollResult.Failed -> {
                    _state.value = AuthUiState.Error(result.message)
                    return
                }
            }
        }
    }

    override fun onCleared() {
        loginJob?.cancel(CancellationException("AuthViewModel cleared"))
        super.onCleared()
    }
}
