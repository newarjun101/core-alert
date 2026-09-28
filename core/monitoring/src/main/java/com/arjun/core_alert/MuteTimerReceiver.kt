package com.arjun.core_alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

class MuteTimerReceiver : BroadcastReceiver(), KoinComponent {

    private val settings: AlertSettingsRepository by inject()

    companion object {
        const val ACTION_UNMUTE = "com.arjun.core_alert.ACTION_UNMUTE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_UNMUTE) {
            settings.clearMute()
            Timber.tag("CoreAlert").i("Mute timer expired. Ringtone override re-enabled.")
        }
    }
}
