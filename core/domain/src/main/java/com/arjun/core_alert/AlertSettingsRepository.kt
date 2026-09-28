package com.arjun.core_alert

import com.arjun.core_alert.models.CallAlertMode
import com.arjun.core_alert.models.QuietRule

interface AlertSettingsRepository {

    val isServiceEnabled: Boolean

    fun setServiceEnabled(enabled: Boolean)

    val volumePercent: Int

    fun setVolumePercent(percent: Int)

    val callAlertMode: CallAlertMode

    fun setCallAlertMode(mode: CallAlertMode)

    val repeatCallWindowMinutes: Int

    fun setRepeatCallWindowMinutes(minutes: Int)

    val escalateCallVolume: Boolean

    fun setEscalateCallVolume(escalate: Boolean)

    val muteUntilTimestamp: Long

    fun muteUntil(epochMs: Long)

    fun clearMute()

    val isMuted: Boolean

    val overrideSoundType: Int

    fun setOverrideSoundType(soundType: Int)

    val messageVolumePercent: Int

    fun setMessageVolumePercent(percent: Int)

    val messageSoundEnabled: Boolean

    fun setMessageSoundEnabled(enabled: Boolean)

    val messageSoundType: Int

    fun setMessageSoundType(soundType: Int)

    val lastPermissionWarningMs: Long

    fun setLastPermissionWarningMs(epochMs: Long)

    fun getQuietRules(): List<QuietRule>

    fun saveQuietRules(rules: List<QuietRule>)

    fun isInQuietPeriod(): Boolean

    fun addAlertChangeListener(listener: () -> Unit)

    fun removeAlertChangeListener(listener: () -> Unit)

    companion object {
        const val MIN_VOLUME_PERCENT = 25
        const val MAX_VOLUME_PERCENT = 100
        const val DEFAULT_VOLUME_PERCENT = 100

        const val MIN_MESSAGE_VOLUME_PERCENT = 5
        const val DEFAULT_MESSAGE_VOLUME_PERCENT = 100
        const val DEFAULT_MESSAGE_SOUND_ENABLED = true
        const val MESSAGE_SOUND_DEFAULT = 0
        const val MESSAGE_SOUND_CONTACT = 1

        const val SOUND_TYPE_RINGTONE = 0
        const val SOUND_TYPE_NOTIFICATION = 1

        const val MAX_QUIET_RULES = 10
    }
}
