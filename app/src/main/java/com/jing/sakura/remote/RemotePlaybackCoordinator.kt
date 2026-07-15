package com.jing.sakura.remote

import android.os.SystemClock
import com.jing.sakura.auth.AulamaAuthRepository
import com.jing.sakura.player.NavigateToPlayerArg
import com.jing.sakura.repo.WebPageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

object RemotePlaybackCoordinator {
    private const val COMMAND_POLL_INTERVAL_MS = 4_000L
    private const val HEARTBEAT_INTERVAL_MS = 15_000L
    private const val MAX_HANDLED_COMMANDS = 128

    private val cycleMutex = Mutex()
    private val ownerMutex = Mutex()
    private val handledCommands = LinkedHashMap<String, RemoteCommandAckStatus>()

    private var activeOwner: Any? = null
    private var ownerGeneration = 0L
    private var lastHeartbeatAt = Long.MIN_VALUE
    private var lastCommandPollAt = Long.MIN_VALUE

    suspend fun runWhileStarted(
        owner: Any,
        authRepository: AulamaAuthRepository,
        webPageRepository: WebPageRepository,
        onOpenAnime: (NavigateToPlayerArg) -> Boolean
    ) {
        val generation = cycleMutex.withLock {
            ownerMutex.withLock {
                ownerGeneration += 1
                activeOwner = owner
                ownerGeneration
            }
        }

        try {
            while (currentCoroutineContext().isActive && isCurrentOwner(owner, generation)) {
                val waitMs = pollWaitMillis()
                if (waitMs > 0L) delay(waitMs)
                cycleMutex.withLock {
                    if (!isCurrentOwner(owner, generation)) return@withLock
                    runCycle(authRepository, webPageRepository, onOpenAnime)
                }
            }
        } finally {
            cycleMutex.withLock {
                ownerMutex.withLock {
                    if (activeOwner === owner && ownerGeneration == generation) {
                        activeOwner = null
                    }
                }
            }
        }
    }

    private suspend fun runCycle(
        authRepository: AulamaAuthRepository,
        webPageRepository: WebPageRepository,
        onOpenAnime: (NavigateToPlayerArg) -> Boolean
    ) {
        currentCoroutineContext().ensureActive()
        if (authRepository.session.value == null) {
            lastHeartbeatAt = Long.MIN_VALUE
            lastCommandPollAt = SystemClock.elapsedRealtime()
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (lastHeartbeatAt == Long.MIN_VALUE || now - lastHeartbeatAt >= HEARTBEAT_INTERVAL_MS) {
            ignoreFailure { authRepository.sendRemoteHeartbeat() }
            lastHeartbeatAt = now
        }

        val command = getOrNull { authRepository.fetchNextRemoteCommand() }
        lastCommandPollAt = SystemClock.elapsedRealtime()
        if (command != null) {
            handleCommand(command, authRepository, webPageRepository, onOpenAnime)
        }
    }

    private suspend fun handleCommand(
        command: RemotePlaybackCommand,
        authRepository: AulamaAuthRepository,
        webPageRepository: WebPageRepository,
        onOpenAnime: (NavigateToPlayerArg) -> Boolean
    ) {
        val previousStatus = handledCommands[command.id]
        if (previousStatus != null) {
            acknowledge(authRepository, command.id, previousStatus)
            return
        }

        val status = when (command) {
            is RemotePlaybackCommand.Invalid -> RemoteCommandAckStatus.FAILED
            is RemotePlaybackCommand.OpenAnime -> {
                val playerArg = getOrNull {
                    buildPlayerArg(command, webPageRepository)
                }
                if (playerArg != null && runCatching { onOpenAnime(playerArg) }.getOrDefault(false)) {
                    RemoteCommandAckStatus.ACCEPTED
                } else {
                    RemoteCommandAckStatus.FAILED
                }
            }
        }
        remember(command.id, status)
        acknowledge(authRepository, command.id, status)
    }

    private suspend fun buildPlayerArg(
        command: RemotePlaybackCommand.OpenAnime,
        repository: WebPageRepository
    ): NavigateToPlayerArg {
        val sourceId = repository.resolveAnimationSourceId(command.sourceId)
        val detail = repository.fetchDetailPage(command.animeId, sourceId)
        val playlistIndex = detail.defaultPlayListIndex
            .takeIf { it in detail.playLists.indices }
            ?: detail.playLists.indexOfFirst { it.defaultPlayList }.takeIf { it >= 0 }
            ?: 0
        val playlist = detail.playLists.getOrNull(playlistIndex)?.episodeList
            ?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Anime playlist is empty")
        if (command.episodeIndex !in playlist.indices) {
            throw IndexOutOfBoundsException("Remote episode index is out of range")
        }
        return NavigateToPlayerArg(
            animeName = detail.animeName,
            animeId = detail.animeId,
            coverUrl = detail.imageUrl,
            playIndex = command.episodeIndex,
            playlist = playlist,
            sourceId = sourceId,
            resumePositionMs = command.currentTimeSeconds
                ?.times(1_000.0)
                ?.roundToLong()
                ?: NavigateToPlayerArg.NO_REMOTE_RESUME_POSITION
        )
    }

    private suspend fun isCurrentOwner(owner: Any, generation: Long): Boolean =
        ownerMutex.withLock { activeOwner === owner && ownerGeneration == generation }

    private fun pollWaitMillis(): Long {
        if (lastCommandPollAt == Long.MIN_VALUE) return 0L
        return (COMMAND_POLL_INTERVAL_MS - (SystemClock.elapsedRealtime() - lastCommandPollAt))
            .coerceAtLeast(0L)
    }

    private fun remember(commandId: String, status: RemoteCommandAckStatus) {
        handledCommands[commandId] = status
        while (handledCommands.size > MAX_HANDLED_COMMANDS) {
            handledCommands.remove(handledCommands.keys.first())
        }
    }

    private suspend fun acknowledge(
        repository: AulamaAuthRepository,
        commandId: String,
        status: RemoteCommandAckStatus
    ) {
        withContext(NonCancellable) {
            runCatching { repository.acknowledgeRemoteCommand(commandId, status) }
        }
    }

    private suspend fun ignoreFailure(block: suspend () -> Unit) {
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // The next foreground cycle retries transient connectivity failures.
        }
    }

    private suspend fun <T> getOrNull(block: suspend () -> T): T? = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }
}
