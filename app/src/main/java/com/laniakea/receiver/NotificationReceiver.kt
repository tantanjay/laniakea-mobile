package com.laniakea.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.laniakea.MainActivity
import com.laniakea.R

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent to open the app normally
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val mainPendingIntent = PendingIntent.getActivity(
            context,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent for the Quick Reflection action
        val quickReflectionIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("OPEN_QUICK_REFLECTION", true)
        }
        val quickReflectionPendingIntent = PendingIntent.getActivity(
            context,
            1, // Different request code
            quickReflectionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hourId = intent.getIntExtra("HOUR_ID", 0)

        val notification = NotificationCompat.Builder(context, "LANIAKEA_NOTIFICATIONS")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Time to Reflect")
            .setContentText("Take a moment to record your thoughts or do a quick check-in.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(mainPendingIntent)
            .setAutoCancel(true)
            .addAction(
                0, // 0 for no icon
                "Quick Reflection",
                quickReflectionPendingIntent
            )
            .build()

        notificationManager.notify(hourId, notification)
    }
}
