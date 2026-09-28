package com.arjun.core_alert

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue
import timber.log.Timber

class BootReceiver : BroadcastReceiver(), KoinComponent {

    private val settings: AlertSettingsRepository by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            if (settings.isServiceEnabled) {
                try {
                    CallMonitorService.start(context)
                    Timber.tag("CoreAlert").i("$action: monitoring service (re)started.")
                } catch (e: Exception) {
                    Timber.tag("CoreAlert").w("$action: could not start monitoring service: ${e.message}")
                }
            }
            val muteUntil = settings.muteUntilTimestamp
            if (muteUntil > System.currentTimeMillis()) {
                scheduleMuteAlarm(context, muteUntil)
                Timber.tag("CoreAlert").i("$action: re-registered mute alarm for $muteUntil")
            }
        }
    }

    companion object {
        fun scheduleMuteAlarm(context: Context, triggerAtMillis: Long) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, MuteTimerReceiver::class.java).apply {
                action = MuteTimerReceiver.ACTION_UNMUTE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
            }
        }

        fun cancelMuteAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, MuteTimerReceiver::class.java).apply {
                action = MuteTimerReceiver.ACTION_UNMUTE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}
