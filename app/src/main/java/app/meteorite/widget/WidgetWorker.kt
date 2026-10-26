package app.meteorite.widget

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Refreshes the widget every 15 minutes (Android's minimum for background jobs; the widget's own
 * period cannot go under 30), once the network is up. A failed download is tried again after
 * 30 s, 1 min, 2 min… up to [MAX_ATTEMPTS] times. The periodic job must stay scheduled while a widget
 * is placed: with no work left, WorkManager turns its receivers off, which makes Android resend
 * APPWIDGET_UPDATE and would loop through onUpdate.
 */
class WidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when {
        WeatherWidget.refresh(applicationContext) -> Result.success()
        runAttemptCount + 1 < MAX_ATTEMPTS -> Result.retry()
        else -> Result.success()
    }

    companion object {
        private const val PERIODIC = "widget-refresh"
        private const val NOW = "widget-refresh-now"
        private const val MAX_ATTEMPTS = 5

        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetWorker>(15, TimeUnit.MINUTES)
                .setConstraints(online)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Right away (city added, order changed, widget placed); waits for the network if needed. */
        fun refreshNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetWorker>()
                .setConstraints(online)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
            WorkManager.getInstance(context).cancelUniqueWork(NOW)
        }
    }
}
