package com.arjun.core_alert

import android.app.NotificationManager

internal class OverrideStateRepositoryImpl(private val source: PrefsDataSource) : OverrideStateRepository {

    private val prefs get() = source.prefs

    override val isActive: Boolean
        get() = prefs.getBoolean(KEY_OVERRIDE_ACTIVE, false)

    override val savedAlarmVolume: Int
        get() = prefs.getInt(KEY_SAVED_ALARM_VOLUME, 0)

    override val savedDndFilter: Int
        get() = prefs.getInt(KEY_SAVED_DND_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL)

    override fun beginOverride(savedAlarmVolume: Int, savedDndFilter: Int) {
        source.put(KEY_SAVED_ALARM_VOLUME, savedAlarmVolume)
        source.put(KEY_SAVED_DND_FILTER, savedDndFilter)
        source.put(KEY_OVERRIDE_ACTIVE, true)
    }

    override fun endOverride() = source.put(KEY_OVERRIDE_ACTIVE, false)

    companion object {
        private const val KEY_OVERRIDE_ACTIVE = "override_active"
        private const val KEY_SAVED_ALARM_VOLUME = "saved_alarm_volume"
        private const val KEY_SAVED_DND_FILTER = "saved_dnd_filter"
    }
}
