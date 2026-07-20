package com.byokeylab.speak

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class MorningTopicReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val topic = TopicRepository.topicForToday()
        ensureChannel(context)

        val launchIntent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_TOPIC, topic)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pendingIntent = PendingIntent.getActivity(
            context,
            2001,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Today's English topic")
            .setContentText(topic)
            .setStyle(NotificationCompat.BigTextStyle().bigText(topic))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(3001, notification)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Daily English topic",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Morning prompts for English conversation practice"
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "daily_topic"
    }
}
