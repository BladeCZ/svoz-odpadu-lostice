package cz.lostice.svozodpadu.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cz.lostice.svozodpadu.data.WasteRepository
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class WasteNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val repo = WasteRepository(applicationContext)
            repo.checkAndSendNotifications()
            scheduleNextAlarms(applicationContext, repo)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "svoz_odpadu_lostice_periodic_check"

        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<WasteNotificationWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )

            val repo = WasteRepository(context)
            scheduleNextAlarms(context, repo)
        }

        fun triggerImmediateCheck(context: Context) {
            val oneTime = OneTimeWorkRequestBuilder<WasteNotificationWorker>().build()
            WorkManager.getInstance(context).enqueue(oneTime)
            val repo = WasteRepository(context)
            scheduleNextAlarms(context, repo)
        }

        fun scheduleNextAlarms(context: Context, repo: WasteRepository) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            if (!repo.isNotificationsEnabled()) return

            val slots = listOf(
                Triple(101, repo.isNotifyTwoDaysBefore(), Pair(repo.getNotifyTwoDaysBeforeHour(), repo.getNotifyTwoDaysBeforeMinute())),
                Triple(102, repo.isNotifyDayBefore(), Pair(repo.getNotifyDayBeforeHour(), repo.getNotifyDayBeforeMinute())),
                Triple(103, repo.isNotifyDayOf(), Pair(repo.getNotifyDayOfHour(), repo.getNotifyDayOfMinute()))
            )

            val now = LocalDateTime.now()
            slots.forEach { (reqCode, enabled, hourMin) ->
                val intent = Intent(context, BootReceiver::class.java).apply {
                    action = BootReceiver.ACTION_ALARM_TRIGGER
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    reqCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                if (!enabled) {
                    alarmManager.cancel(pi)
                } else {
                    var target = now.withHour(hourMin.first).withMinute(hourMin.second).withSecond(5).withNano(0)
                    if (!target.isAfter(now)) {
                        target = target.plusDays(1)
                    }
                    val epochMillis = target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    try {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMillis, pi)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }
}
