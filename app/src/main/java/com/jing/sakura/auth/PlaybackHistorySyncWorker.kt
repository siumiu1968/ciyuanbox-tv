package com.jing.sakura.auth

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

class PlaybackHistorySyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val koin = runCatching { GlobalContext.get() }.getOrNull() ?: return Result.retry()
        val repository = koin.get<AulamaAuthRepository>()
        val accountKey = repository.session.value?.account?.email.orEmpty()
        if (accountKey.isBlank()) return Result.success()

        val queue = koin.get<PlaybackHistorySyncQueue>()
        var failed = false
        queue.pendingForAccount(accountKey).forEach { queued ->
            if (repository.syncPlaybackHistory(queued.payload)) {
                queue.removeIfCurrent(accountKey, queued.payload)
            } else {
                failed = true
            }
        }
        return if (failed && repository.session.value != null) Result.retry() else Result.success()
    }
}

object PlaybackHistorySyncScheduler {
    private const val UNIQUE_WORK = "aulama-playback-history-sync"

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<PlaybackHistorySyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
