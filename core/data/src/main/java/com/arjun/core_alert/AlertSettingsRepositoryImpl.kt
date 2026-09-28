package com.arjun.core_alert

import com.arjun.core_alert.AlertSettingsRepository.Companion.DEFAULT_MESSAGE_SOUND_ENABLED
import com.arjun.core_alert.AlertSettingsRepository.Companion.DEFAULT_MESSAGE_VOLUME_PERCENT
import com.arjun.core_alert.AlertSettingsRepository.Companion.DEFAULT_VOLUME_PERCENT
import com.arjun.core_alert.AlertSettingsRepository.Companion.MAX_VOLUME_PERCENT
import com.arjun.core_alert.AlertSettingsRepository.Companion.MESSAGE_SOUND_CONTACT
import com.arjun.core_alert.AlertSettingsRepository.Companion.MESSAGE_SOUND_DEFAULT
import com.arjun.core_alert.AlertSettingsRepository.Companion.MIN_MESSAGE_VOLUME_PERCENT
import com.arjun.core_alert.AlertSettingsRepository.Companion.MIN_VOLUME_PERCENT
import com.arjun.core_alert.AlertSettingsRepository.Companion.SOUND_TYPE_RINGTONE
import com.arjun.core_alert.models.CallAlertMode
import com.arjun.core_alert.models.QuietRule
import com.arjun.core_alert.policy.MutePolicy
import com.arjun.core_alert.policy.QuietHoursPolicy
import com.arjun.core_alert.policy.RepeatCallPolicy
import java.util.Calendar
import org.json.JSONArray
import org.json.JSONObject

internal class AlertSettingsRepositoryImpl(private val source: PrefsDataSource) : AlertSettingsRepository {

    private val prefs get() = source.prefs

    override val isServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)

    override fun setServiceEnabled(enabled: Boolean) =
        source.put(KEY_SERVICE_ENABLED, enabled, callScoped = true)

    override val volumePercent: Int
        get() = prefs.getInt(KEY_VOLUME_PERCENT, DEFAULT_VOLUME_PERCENT)

    override fun setVolumePercent(percent: Int) = source.put(
        KEY_VOLUME_PERCENT,
        percent.coerceIn(MIN_VOLUME_PERCENT, MAX_VOLUME_PERCENT),
        callScoped = true
    )

    override val callAlertMode: CallAlertMode
        get() = CallAlertMode.parse(prefs.getString(KEY_CALL_ALERT_MODE, null)).let {
            if (it == CallAlertMode.INHERIT) CallAlertMode.FIRST else it
        }

    override fun setCallAlertMode(mode: CallAlertMode) =
        source.put(KEY_CALL_ALERT_MODE, mode.name, callScoped = true)

    override val repeatCallWindowMinutes: Int
        get() = RepeatCallPolicy.windowMinutes(prefs.getInt(KEY_REPEAT_CALL_WINDOW, DEFAULT_REPEAT_CALL_WINDOW_MINUTES))

    override fun setRepeatCallWindowMinutes(minutes: Int) = source.put(
        KEY_REPEAT_CALL_WINDOW,
        RepeatCallPolicy.windowMinutes(minutes),
        callScoped = true
    )

    override val escalateCallVolume: Boolean
        get() = prefs.getBoolean(KEY_ESCALATE_CALL_VOLUME, false)

    override fun setEscalateCallVolume(escalate: Boolean) =
        source.put(KEY_ESCALATE_CALL_VOLUME, escalate, callScoped = true)

    override val muteUntilTimestamp: Long
        get() = prefs.getLong(KEY_MUTE_UNTIL, 0L)

    override fun muteUntil(epochMs: Long) = source.put(KEY_MUTE_UNTIL, epochMs, callScoped = true)

    override fun clearMute() = source.put(KEY_MUTE_UNTIL, 0L, callScoped = true)

    override val isMuted: Boolean
        get() {
            val until = muteUntilTimestamp
            val muted = MutePolicy.isMuted(until, System.currentTimeMillis())
            if (!muted && until != 0L) clearMute()
            return muted
        }

    override val overrideSoundType: Int
        get() = prefs.getInt(KEY_OVERRIDE_SOUND_TYPE, SOUND_TYPE_RINGTONE)

    override fun setOverrideSoundType(soundType: Int) = source.put(KEY_OVERRIDE_SOUND_TYPE, soundType)

    override val messageVolumePercent: Int
        get() = prefs.getInt(KEY_MESSAGE_VOLUME_PERCENT, DEFAULT_MESSAGE_VOLUME_PERCENT)
            .coerceIn(MIN_MESSAGE_VOLUME_PERCENT, MAX_VOLUME_PERCENT)

    override fun setMessageVolumePercent(percent: Int) = source.put(
        KEY_MESSAGE_VOLUME_PERCENT,
        percent.coerceIn(MIN_MESSAGE_VOLUME_PERCENT, MAX_VOLUME_PERCENT)
    )

    override val messageSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_MESSAGE_SOUND_ENABLED, DEFAULT_MESSAGE_SOUND_ENABLED)

    override fun setMessageSoundEnabled(enabled: Boolean) = source.put(KEY_MESSAGE_SOUND_ENABLED, enabled)

    override val messageSoundType: Int
        get() = prefs.getInt(KEY_MESSAGE_SOUND_TYPE, MESSAGE_SOUND_DEFAULT).let {
            if (it == MESSAGE_SOUND_CONTACT) MESSAGE_SOUND_CONTACT else MESSAGE_SOUND_DEFAULT
        }

    override fun setMessageSoundType(soundType: Int) = source.put(
        KEY_MESSAGE_SOUND_TYPE,
        if (soundType == MESSAGE_SOUND_CONTACT) MESSAGE_SOUND_CONTACT else MESSAGE_SOUND_DEFAULT
    )

    override val lastPermissionWarningMs: Long
        get() = prefs.getLong(KEY_LAST_PERM_WARNING, 0L)

    override fun setLastPermissionWarningMs(epochMs: Long) = source.put(KEY_LAST_PERM_WARNING, epochMs)

    override fun getQuietRules(): List<QuietRule> {
        val json = prefs.getString(KEY_QUIET_RULES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val daysArr = obj.getJSONArray("days")
                val days = (0 until daysArr.length()).map { daysArr.getInt(it) }.toSet()
                QuietRule(
                    days = days,
                    startHour = obj.getInt("startHour"),
                    startMinute = obj.getInt("startMinute"),
                    endHour = obj.getInt("endHour"),
                    endMinute = obj.getInt("endMinute")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun saveQuietRules(rules: List<QuietRule>) {
        val arr = JSONArray()
        rules.forEach { rule ->
            arr.put(JSONObject().apply {
                put("days", JSONArray(rule.days.toList()))
                put("startHour", rule.startHour)
                put("startMinute", rule.startMinute)
                put("endHour", rule.endHour)
                put("endMinute", rule.endMinute)
            })
        }
        source.put(KEY_QUIET_RULES, arr.toString(), callScoped = true)
    }

    override fun isInQuietPeriod(): Boolean {
        val rules = getQuietRules()
        if (rules.isEmpty()) return false

        val cal = Calendar.getInstance()
        val currentDay = cal.get(Calendar.DAY_OF_WEEK)
        val currentTime = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return QuietHoursPolicy.isQuiet(rules, currentDay, currentTime)
    }

    override fun addAlertChangeListener(listener: () -> Unit) = source.addAlertChangeListener(listener)

    override fun removeAlertChangeListener(listener: () -> Unit) = source.removeAlertChangeListener(listener)

    companion object {
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_VOLUME_PERCENT = "volume_percent"
        private const val KEY_CALL_ALERT_MODE = "call_alert_mode"
        private const val KEY_REPEAT_CALL_WINDOW = "repeat_call_window_minutes"
        private const val KEY_ESCALATE_CALL_VOLUME = "escalate_call_volume"
        private const val KEY_MUTE_UNTIL = "mute_until_timestamp"
        private const val KEY_OVERRIDE_SOUND_TYPE = "override_sound_type"
        private const val KEY_MESSAGE_VOLUME_PERCENT = "message_volume_percent"
        private const val KEY_MESSAGE_SOUND_ENABLED = "message_sound_enabled"
        private const val KEY_MESSAGE_SOUND_TYPE = "message_sound_type"
        private const val KEY_QUIET_RULES = "quiet_rules"
        private const val KEY_LAST_PERM_WARNING = "last_perm_warning"

        private const val DEFAULT_REPEAT_CALL_WINDOW_MINUTES = 5
    }
}
