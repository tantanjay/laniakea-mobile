package com.laniakea.manager

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.laniakea.receiver.NotificationReceiver
import java.util.Calendar

class NotificationScheduler(private val context: Context) {
    
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Daily Reflection"
            val descriptionText = "Reminders to reflect on your day"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("LANIAKEA_NOTIFICATIONS", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun scheduleNotifications(hours: List<Int>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // First cancel any existing alarms. We can do this by iterating through 24 possible hours
        for (i in 0..23) {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                putExtra("HOUR_ID", i)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                i,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }

        // Schedule new alarms
        for (hour in hours) {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                putExtra("HOUR_ID", hour)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                hour,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                
                // If the time has already passed today, schedule for tomorrow
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        }
    }

    fun cancelAllNotifications() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (i in 0..23) {
            val intent = Intent(context, NotificationReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                i,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}
