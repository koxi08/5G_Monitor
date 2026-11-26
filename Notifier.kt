package com.kosigan.fiveguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.provider.Settings

object Notifier {
    private const val CH_MONITOR = "monitor"
    private const val CH_ALERT = "alert"
    const val ID_MONITOR = 1
    const val ID_ALERT = 2

    fun createChannels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_MONITOR, "Monitoring status", NotificationManager.IMPORTANCE_LOW)
        )
        val alert = NotificationChannel(CH_ALERT, "Network alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 800)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        nm.createNotificationChannel(alert)
    }

    private fun openApp(c: Context): PendingIntent = PendingIntent.getActivity(
        c, 0, Intent(c, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun monitor(c: Context, text: String): Notification =
        Notification.Builder(c, CH_MONITOR)
            .setSmallIcon(R.drawable.ic_signal)
            .setContentTitle("5G Guard is watching")
            .setContentText(text)
            .setContentIntent(openApp(c))
            .setOngoing(true)
            .build()

    fun updateMonitor(c: Context, text: String) {
        c.getSystemService(NotificationManager::class.java).notify(ID_MONITOR, monitor(c, text))
    }

    /** loud = true for the downgrade alarm; false for quiet info like "back on 5G". */
    fun alert(c: Context, title: String, text: String, offerRestore: Boolean, loud: Boolean = true) {
        val b = Notification.Builder(c, if (loud) CH_ALERT else CH_MONITOR)
            .setSmallIcon(R.drawable.ic_signal)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openApp(c))
            .setAutoCancel(true)

        if (offerRestore) {
            val stop = PendingIntent.getService(
                c, 1,
                Intent(c, BlockVpnService::class.java).setAction(BlockVpnService.ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            b.addAction(Notification.Action.Builder(null, "Turn internet back on", stop).build())
        }
        val settings = PendingIntent.getActivity(
            c, 2, Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        b.addAction(Notification.Action.Builder(null, "Network settings", settings).build())

        c.getSystemService(NotificationManager::class.java).notify(ID_ALERT, b.build())
    }
}
