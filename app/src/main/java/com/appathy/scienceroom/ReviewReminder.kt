package com.appathy.scienceroom

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.appathy.scienceroom.data.Content
import com.appathy.scienceroom.data.PlayerRepo
import com.appathy.scienceroom.engine.ReviewEngine
import java.util.Calendar

/** 間隔反復は思い出すきっかけがないと働かないので、1日1回だけ知らせる */
object ReviewReminder {

    const val CHANNEL_ID = "review_reminder"
    private const val REQUEST_CODE = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "復習のお知らせ",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.description = "覚えた元素の復習どきを知らせます"
        manager.createNotificationChannel(channel)
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReviewReceiver::class.java)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    fun schedule(context: Context, hour: Int) {
        ensureChannel(context)
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        manager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent(context)
        )
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        manager.cancel(pendingIntent(context))
    }

    /** 設定に合わせて予約を張り直す */
    fun sync(context: Context) {
        val state = PlayerRepo.load(context)
        if (state.reminderEnabled) schedule(context, state.reminderHour) else cancel(context)
    }
}

class ReviewReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val state = PlayerRepo.load(context)
        if (!state.reminderEnabled) return

        val due = try {
            ReviewEngine.dueCount(Content.load(context), state, System.currentTimeMillis())
        } catch (e: Exception) {
            0
        }

        val text = if (due > 0) "復習どきの元素が " + due + " 個あります"
        else "今日の元素クイズはまだです"

        ReviewReminder.ensureChannel(context)

        val open = Intent(context, MainActivity::class.java)
        open.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        val contentIntent = PendingIntent.getActivity(context, 2001, open, flags)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(context, ReviewReminder.CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(context)
        }

        val notification = builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("科学室")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        try {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.notify(3001, notification)
        } catch (e: SecurityException) {
            // 通知が許可されていない場合は何もしない
        }
    }
}
