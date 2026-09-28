package com.arjun.core_alert

import com.arjun.core_alert.models.CallAlertMode
import com.arjun.core_alert.models.QuietRule
import com.arjun.core_alert.models.VipContact
import com.arjun.core_alert.policy.QuietRulePolicy
import com.arjun.core_alert.policy.RepeatCallPolicy
import org.json.JSONArray
import org.json.JSONObject

data class AppConfig(
    val version: Int,
    val exportedAt: Long,
    val volumePercent: Int,
    val overrideSoundType: Int,
    val messageVolumePercent: Int,
    val messageSoundEnabled: Boolean,
    val messageSoundType: Int,
    val contacts: List<VipContact>,
    val quietRules: List<QuietRule>,
    val callAlertMode: CallAlertMode = CallAlertMode.FIRST,
    val repeatCallWindowMinutes: Int = 5,
    val escalateCallVolume: Boolean = false
)

object ConfigExporter {

    private const val CURRENT_VERSION = 1

    fun isValidRuleRanges(
        startHour: Int, startMinute: Int, endHour: Int, endMinute: Int, days: Set<Int>
    ): Boolean {
        return startHour in 0..23 && endHour in 0..23 &&
            startMinute in 0..59 && endMinute in 0..59 &&
            days.isNotEmpty() && days.all { it in 1..7 } &&
            QuietRulePolicy.isValid(days, startHour, startMinute, endHour, endMinute)
    }

    fun buildConfig(
        volumePercent: Int,
        overrideSoundType: Int,
        messageVolumePercent: Int,
        messageSoundEnabled: Boolean,
        messageSoundType: Int,
        contacts: List<VipContact>,
        quietRules: List<QuietRule>,
        callAlertMode: CallAlertMode = CallAlertMode.FIRST,
        repeatCallWindowMinutes: Int = 5,
        escalateCallVolume: Boolean = false
    ): AppConfig = AppConfig(
        version = CURRENT_VERSION,
        exportedAt = System.currentTimeMillis(),
        volumePercent = volumePercent,
        overrideSoundType = overrideSoundType,
        messageVolumePercent = messageVolumePercent,
        messageSoundEnabled = messageSoundEnabled,
        messageSoundType = messageSoundType,
        contacts = contacts,
        quietRules = quietRules,
        callAlertMode = callAlertMode,
        repeatCallWindowMinutes = repeatCallWindowMinutes,
        escalateCallVolume = escalateCallVolume
    )

    fun export(config: AppConfig): String {
        val contactsArr = JSONArray()
        config.contacts.forEach { c ->
            contactsArr.put(JSONObject().apply {
                put("name", c.name)
                put("number", c.number)
                put("ringtoneEnabled", c.ringtoneEnabled)
                put("callAlertMode", c.callAlertMode.name)
            })
        }
        val rulesArr = JSONArray()
        config.quietRules.forEach { r ->
            rulesArr.put(JSONObject().apply {
                put("days", JSONArray(r.days.toList()))
                put("startHour", r.startHour)
                put("startMinute", r.startMinute)
                put("endHour", r.endHour)
                put("endMinute", r.endMinute)
            })
        }
        val root = JSONObject().apply {
            put("version", config.version)
            put("exportedAt", config.exportedAt)
            put("volumePercent", config.volumePercent)
            put("overrideSoundType", config.overrideSoundType)
            put("messageVolumePercent", config.messageVolumePercent)
            put("messageSoundEnabled", config.messageSoundEnabled)
            put("messageSoundType", config.messageSoundType)
            put("callAlertMode", config.callAlertMode.name)
            put("repeatCallWindowMinutes", config.repeatCallWindowMinutes)
            put("escalateCallVolume", config.escalateCallVolume)
            put("contacts", contactsArr)
            put("quietRules", rulesArr)
        }
        return root.toString(2)
    }

    fun import(json: String): AppConfig? {
        return try {
            val root = JSONObject(json)
            val version = root.optInt("version", CURRENT_VERSION)
            val exportedAt = root.optLong("exportedAt", 0L)
            val volumePercent = root.optInt("volumePercent", AlertSettingsRepository.DEFAULT_VOLUME_PERCENT)
            val overrideSoundType = root.optInt("overrideSoundType", AlertSettingsRepository.SOUND_TYPE_RINGTONE)
            val messageVolumePercent = root.optInt(
                "messageVolumePercent",
                AlertSettingsRepository.DEFAULT_MESSAGE_VOLUME_PERCENT
            )
            val messageSoundEnabled = root.optBoolean(
                "messageSoundEnabled",
                AlertSettingsRepository.DEFAULT_MESSAGE_SOUND_ENABLED
            )
            val messageSoundType = root.optInt("messageSoundType", AlertSettingsRepository.MESSAGE_SOUND_DEFAULT)

            val contactsArr = root.optJSONArray("contacts")
            val contacts = mutableListOf<VipContact>()
            if (contactsArr != null) {
                for (i in 0 until contactsArr.length()) {
                    val obj = contactsArr.getJSONObject(i)
                    contacts.add(VipContact(
                        name = obj.optString("name", ""),
                        number = obj.optString("number", ""),
                        ringtoneEnabled = obj.optBoolean("ringtoneEnabled", true),
                        callAlertMode = CallAlertMode.parse(obj.optString("callAlertMode"), CallAlertMode.INHERIT)
                    ))
                }
            }

            val rulesArr = root.optJSONArray("quietRules")
            val rules = mutableListOf<QuietRule>()
            if (rulesArr != null) {
                for (i in 0 until rulesArr.length()) {
                    val obj = rulesArr.getJSONObject(i)
                    val daysArr = obj.optJSONArray("days")
                    val days = mutableSetOf<Int>()
                    if (daysArr != null) {
                        for (j in 0 until daysArr.length()) {
                            days.add(daysArr.getInt(j))
                        }
                    }
                    val sh = obj.optInt("startHour", 0)
                    val sm = obj.optInt("startMinute", 0)
                    val eh = obj.optInt("endHour", 0)
                    val em = obj.optInt("endMinute", 0)
                    if (isValidRuleRanges(sh, sm, eh, em, days)) {
                        rules.add(QuietRule(
                            days = days,
                            startHour = sh,
                            startMinute = sm,
                            endHour = eh,
                            endMinute = em
                        ))
                    }
                }
            }

            AppConfig(
                version = version,
                exportedAt = exportedAt,
                volumePercent = volumePercent,
                overrideSoundType = overrideSoundType,
                messageVolumePercent = messageVolumePercent,
                messageSoundEnabled = messageSoundEnabled,
                messageSoundType = messageSoundType,
                contacts = contacts,
                quietRules = rules,
                callAlertMode = CallAlertMode.parse(root.optString("callAlertMode")).let {
                    if (it == CallAlertMode.INHERIT) CallAlertMode.FIRST else it
                },
                repeatCallWindowMinutes = RepeatCallPolicy.windowMinutes(root.optInt("repeatCallWindowMinutes", 5)),
                escalateCallVolume = root.optBoolean("escalateCallVolume", false)
            )
        } catch (e: Exception) {
            null
        }
    }
}
