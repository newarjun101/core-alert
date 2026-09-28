package com.arjun.core_alert

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.CopyOnWriteArraySet

internal class PrefsDataSource(val context: Context) {

    val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val callAttempts: SharedPreferences = context.getSharedPreferences(CALL_ATTEMPTS_NAME, Context.MODE_PRIVATE)

    private val alertChangeListeners = CopyOnWriteArraySet<() -> Unit>()

    private val changeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && key in CALL_SCOPED_KEYS) alertChangeListeners.forEach { it() }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(changeListener)
    }

    fun put(key: String, value: Any?, callScoped: Boolean = false) {
        if (prefs.all[key] == value) return
        if (callScoped) callAttempts.edit().clear().apply()
        prefs.edit().apply {
            when (value) {
                null -> putString(key, null)
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is String -> putString(key, value)
                else -> error("Unsupported preference type for '$key': ${value::class.qualifiedName}")
            }
        }.apply()
    }

    fun addAlertChangeListener(listener: () -> Unit) {
        alertChangeListeners += listener
    }

    fun removeAlertChangeListener(listener: () -> Unit) {
        alertChangeListeners -= listener
    }

    companion object {
        const val PREFS_NAME = "corealert_prefs"
        const val CALL_ATTEMPTS_NAME = "corealert_call_attempts"

        private val CALL_SCOPED_KEYS = setOf(
            "service_enabled",
            "mute_until_timestamp",
            "quiet_rules",
            "vip_contacts",
            "call_alert_mode",
            "repeat_call_window_minutes",
            "escalate_call_volume",
            "volume_percent"
        )
    }
}
