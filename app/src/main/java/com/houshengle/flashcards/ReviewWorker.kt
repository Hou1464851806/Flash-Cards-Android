package com.houshengle.flashcards

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ReviewWorker(
    private val context: Context, workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {

    private var cardsNumToReview: Int = 0

    companion object {
        const val CHANNEL_ID = "REVIEW_CHANNEL"
        const val NOTIFICATION_ID = 1
        const val PARAM_1 = "param_1"
    }

    override suspend fun doWork(): Result {
        val param1 = inputData.getString(PARAM_1) ?: return Result.failure()
        Log.i("后台服务", param1)
        cardsNumToReview = param1.toInt()
        if (cardsNumToReview != 0) {
            sendNotification(cardsNumToReview)
        }

        val currentDate = Calendar.getInstance()
        val dueDate = Calendar.getInstance()

        dueDate.set(Calendar.HOUR_OF_DAY, 5)
        dueDate.set(Calendar.MINUTE, 0)
        dueDate.set(Calendar.SECOND, 0)
        if (dueDate.before(currentDate)) {
            dueDate.add(Calendar.HOUR_OF_DAY, 24)
        }
        val timeDiff = dueDate.timeInMillis - currentDate.timeInMillis
        val dailyWorkRequest = OneTimeWorkRequestBuilder<ReviewWorker>().setInitialDelay(
                timeDiff,
                TimeUnit.MILLISECONDS
            ).addTag("Worker").build()

        WorkManager.getInstance(applicationContext).enqueue(dailyWorkRequest)

        return Result.success()
    }

    private fun sendNotification(cardCount: Int) {
        createNotificationChannel()

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID).setSmallIcon(
            R.drawable.ic_launcher_foreground
        ).setContentTitle("复习提醒").setContentText("您有 $cardCount 张卡片需要复习")
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e("后台服务", "没有通知权限")
                return
            }
            notify(NOTIFICATION_ID, notificationBuilder.build())
        }

    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "ReviewChannel"
            val descriptionText = "Channel for review reminders"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

}