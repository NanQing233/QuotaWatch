package io.apimeter.wear.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.apimeter.wear.ApiMeterApplication
import io.apimeter.wear.storage.AppSettings
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/** 后台周期同步；下限取 WorkManager 官方限制的 15 分钟。 */
object UsageSync {

    private const val UNIQUE_WORK = "apimeter-periodic-sync"
    const val TAG = "apimeter-sync"
    const val MIN_INTERVAL_MINUTES = 15

    fun ensureScheduled(context: Context, settings: AppSettings) {
        val workManager = WorkManager.getInstance(context)
        if (!settings.backgroundSync) {
            workManager.cancelUniqueWork(UNIQUE_WORK)
            return
        }
        val interval = settings.refreshIntervalMinutes.coerceIn(MIN_INTERVAL_MINUTES, 1440)
        val request = PeriodicWorkRequestBuilder<UsageSyncWorker>(interval.toLong(), TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .addTag(TAG)
            .build()
        workManager.enqueueUniquePeriodicWork(UNIQUE_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
    }
}

class UsageSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? ApiMeterApplication ?: return Result.failure()
        return try {
            app.container.usageStore.refreshAll(force = true)
            Result.success()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (other: Throwable) {
            Result.retry()
        }
    }
}
