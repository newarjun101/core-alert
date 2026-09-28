package com.arjun.core_alert.feature.settings

import android.content.res.Resources
import com.arjun.core_alert.feature.R
import com.arjun.core_alert.models.AppLanguage
import com.arjun.core_alert.models.AppPalette
import com.arjun.core_alert.models.CallAlertMode
import com.arjun.core_alert.models.NightMode
import com.arjun.core_alert.models.QuietRule
import java.util.Calendar
import java.util.Locale

data class SettingsUiState(
    val volumePercent: Int = 0,
    val messageVolumePercent: Int = 0,
    val messageSoundEnabled: Boolean = false,
    val messageSoundType: Int = 0,
    val overrideSoundType: Int = 0,
    val themePalette: AppPalette = AppPalette.INDACO,
    val nightMode: NightMode = NightMode.FOLLOW_SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val quietRules: List<QuietRule> = emptyList(),
    val messageAlertsSupported: Boolean = false,
    val callAlertMode: CallAlertMode = CallAlertMode.FIRST,
    val repeatWindowMinutes: Int = 5,
    val escalateCallVolume: Boolean = false,
    val dialog: SettingsDialog? = null
)

internal fun formatRuleDays(res: Resources, rule: QuietRule): String {
    if (rule.days.size == 7) return res.getString(R.string.quiet_every_day)
    val dayNames = mapOf(
        Calendar.MONDAY to res.getString(R.string.day_mon),
        Calendar.TUESDAY to res.getString(R.string.day_tue),
        Calendar.WEDNESDAY to res.getString(R.string.day_wed),
        Calendar.THURSDAY to res.getString(R.string.day_thu),
        Calendar.FRIDAY to res.getString(R.string.day_fri),
        Calendar.SATURDAY to res.getString(R.string.day_sat),
        Calendar.SUNDAY to res.getString(R.string.day_sun)
    )
    val orderedDays = listOf(
        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
        Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
    )
    val sorted = orderedDays.filter { it in rule.days }
    if (sorted.size >= 2) {
        val first = orderedDays.indexOf(sorted.first())
        val last = orderedDays.indexOf(sorted.last())
        if (last - first + 1 == sorted.size) {
            return "${dayNames[sorted.first()]}-${dayNames[sorted.last()]}"
        }
    }
    return sorted.mapNotNull { dayNames[it] }.joinToString(", ")
}

internal fun formatRuleTime(res: Resources, rule: QuietRule): String {
    val from = String.format(Locale.US, "%02d:%02d", rule.startHour, rule.startMinute)
    val to = String.format(Locale.US, "%02d:%02d", rule.endHour, rule.endMinute)
    val crossMidnight = rule.endHour * 60 + rule.endMinute <= rule.startHour * 60 + rule.startMinute
    return if (crossMidnight) "$from - $to ${res.getString(R.string.quiet_next_day)}" else "$from - $to"
}
