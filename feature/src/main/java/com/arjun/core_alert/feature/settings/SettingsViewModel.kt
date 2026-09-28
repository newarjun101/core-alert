package com.arjun.core_alert.feature.settings

import android.app.Activity
import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.arjun.core_alert.AlertSettingsRepository
import com.arjun.core_alert.AppConfig
import com.arjun.core_alert.models.AppLanguage
import com.arjun.core_alert.models.AppPalette
import com.arjun.core_alert.CallMonitorService
import com.arjun.core_alert.ConfigCrypto
import com.arjun.core_alert.ConfigExporter
import com.arjun.core_alert.ContactRepository
import com.arjun.core_alert.LanguageManager
import com.arjun.core_alert.models.NightMode
import com.arjun.core_alert.models.QuietRule
import com.arjun.core_alert.policy.QuietRulePolicy
import com.arjun.core_alert.feature.R
import com.arjun.core_alert.ThemeManager
import com.arjun.core_alert.ThemeRepository
import com.arjun.core_alert.VipMessageAlertsProvider
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class SettingsViewModel(
    private val context: Application,
    val settings: AlertSettingsRepository,
    val theme: ThemeRepository,
    private val contactRepository: ContactRepository,
    private val onContactsChanged: () -> Unit,
    val messageAlerts: VipMessageAlertsProvider
) : ViewModel() {

    var volumePercent by mutableStateOf(settings.volumePercent)
    var messageVolumePercent by mutableStateOf(settings.messageVolumePercent)
    var messageSoundEnabled by mutableStateOf(settings.messageSoundEnabled)
    var messageSoundType by mutableStateOf(settings.messageSoundType)
    var overrideSoundType by mutableStateOf(settings.overrideSoundType)
    var themePalette by mutableStateOf(theme.palette)
    var nightMode by mutableStateOf(theme.nightMode)
    var language by mutableStateOf(LanguageManager.current(context))
    var quietRules by mutableStateOf(settings.getQuietRules())
    var dialog by androidx.compose.runtime.mutableStateOf<SettingsDialog?>(null)

    private var pendingExportPassword: String? = null
    private var pendingImportText: String? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    fun selectPalette(palette: AppPalette) {
        if (palette == theme.palette) return
        theme.setPalette(palette)
        themePalette = palette
        ThemeManager.applyPalette(palette)
    }

    fun selectNightMode(mode: NightMode) {
        if (mode != theme.nightMode) {
            theme.setNightMode(mode)
            nightMode = mode
            ThemeManager.applyNightMode(mode)
        }
    }

    fun selectLanguage(selected: AppLanguage, activity: Activity? = null) {
        if (selected == language) return
        LanguageManager.apply(context, selected, activity)
        language = LanguageManager.current(context)
    }

    fun setVolume(value: Int) {
        volumePercent = value
        settings.setVolumePercent(value)
    }

    fun updateOverrideSoundType(type: Int) {
        overrideSoundType = type
        settings.setOverrideSoundType(type)
    }

    fun updateMessageSoundEnabled(enabled: Boolean) {
        messageSoundEnabled = enabled
        settings.setMessageSoundEnabled(enabled)
        if (!enabled) CallMonitorService.getInstance()?.suspendMessageAlert()
    }

    fun setMessageVolume(value: Int) {
        messageVolumePercent = value
        settings.setMessageVolumePercent(value)
    }

    fun updateMessageSoundType(type: Int) {
        messageSoundType = type
        settings.setMessageSoundType(type)
    }

    fun openExportPasswordDialog() { dialog = SettingsDialog.ExportPassword }

    /** Stores the export password and returns the suggested document name. */
    fun beginExport(password: String): String {
        pendingExportPassword = password
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "corealert-backup-$timestamp.json"
    }

    fun openImportConfirm() { dialog = SettingsDialog.ImportConfirm }

    fun onExportUri(uri: Uri) {
        val password = pendingExportPassword
        pendingExportPassword = null
        if (password.isNullOrEmpty()) return
        try {
            val config = ConfigExporter.buildConfig(
                volumePercent = settings.volumePercent,
                overrideSoundType = settings.overrideSoundType,
                messageVolumePercent = settings.messageVolumePercent,
                messageSoundEnabled = settings.messageSoundEnabled,
                messageSoundType = settings.messageSoundType,
                contacts = contactRepository.getContacts(),
                quietRules = settings.getQuietRules(),
                callAlertMode = settings.callAlertMode,
                repeatCallWindowMinutes = settings.repeatCallWindowMinutes,
                escalateCallVolume = settings.escalateCallVolume
            )
            val envelope = ConfigCrypto.encrypt(ConfigExporter.export(config), password)
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(envelope.toByteArray(Charsets.UTF_8))
            }
            toast(context.getString(R.string.backup_export_success))
        } catch (e: Exception) {
            toast(context.getString(R.string.backup_export_failed, e.message ?: "unknown"), long = true)
        }
    }

    fun onImportUri(uri: Uri) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { isr ->
                isr.bufferedReader(Charsets.UTF_8).readText()
            } ?: return

            if (!ConfigCrypto.isEncryptedEnvelope(text)) {
                toast(context.getString(R.string.backup_import_legacy_rejected), long = true)
                return
            }
            pendingImportText = text
            dialog = SettingsDialog.ImportPassword
        } catch (e: Exception) {
            toast(context.getString(R.string.backup_import_failed), long = true)
        }
    }

    fun decryptPendingImport(password: String) {
        val text = pendingImportText ?: return
        pendingImportText = null
        Thread {
            val plaintext = ConfigCrypto.decrypt(text, password)
            val config = plaintext?.let { ConfigExporter.import(it) }
            mainHandler.post {
                dialog = null
                when {
                    plaintext == null -> toast(context.getString(R.string.backup_import_wrong_password), long = true)
                    config == null -> toast(context.getString(R.string.backup_import_failed), long = true)
                    else -> applyImportedConfig(config)
                }
            }
        }.start()
    }

    private fun applyImportedConfig(config: AppConfig) {
        settings.setVolumePercent(config.volumePercent)
        settings.setOverrideSoundType(config.overrideSoundType)
        settings.setMessageVolumePercent(config.messageVolumePercent)
        settings.setMessageSoundEnabled(config.messageSoundEnabled)
        settings.setMessageSoundType(config.messageSoundType)
        settings.setCallAlertMode(config.callAlertMode)
        settings.setRepeatCallWindowMinutes(config.repeatCallWindowMinutes)
        settings.setEscalateCallVolume(config.escalateCallVolume)
        contactRepository.saveContacts(config.contacts)
        settings.saveQuietRules(config.quietRules)

        volumePercent = config.volumePercent
        overrideSoundType = config.overrideSoundType
        messageVolumePercent = config.messageVolumePercent
        messageSoundEnabled = config.messageSoundEnabled
        messageSoundType = config.messageSoundType
        quietRules = settings.getQuietRules()
        onContactsChanged()

        toast(context.getString(R.string.backup_import_success))

        if (settings.isServiceEnabled) {
            CallMonitorService.stop(context)
            CallMonitorService.start(context)
        }
    }

    fun openAddQuietRule() {
        if (quietRules.size >= AlertSettingsRepository.MAX_QUIET_RULES) {
            toast(context.getString(R.string.quiet_max_rules))
        } else {
            dialog = SettingsDialog.AddQuietRule
        }
    }

    fun addQuietRule(rule: QuietRule): Boolean {
        if (!QuietRulePolicy.isValid(rule.days, rule.startHour, rule.startMinute, rule.endHour, rule.endMinute)) {
            toast(context.getString(R.string.quiet_invalid_rule))
            return false
        }
        quietRules = quietRules + rule
        settings.saveQuietRules(quietRules)
        if (settings.isInQuietPeriod()) CallMonitorService.getInstance()?.suspendActiveAlert()
        return true
    }

    fun deleteQuietRule(index: Int) {
        quietRules = quietRules.filterIndexed { i, _ -> i != index }
        settings.saveQuietRules(quietRules)
    }

    fun formatRuleDays(rule: QuietRule): String {
        if (rule.days.size == 7) return context.getString(R.string.quiet_every_day)
        val dayNames = mapOf(
            Calendar.MONDAY to context.getString(R.string.day_mon),
            Calendar.TUESDAY to context.getString(R.string.day_tue),
            Calendar.WEDNESDAY to context.getString(R.string.day_wed),
            Calendar.THURSDAY to context.getString(R.string.day_thu),
            Calendar.FRIDAY to context.getString(R.string.day_fri),
            Calendar.SATURDAY to context.getString(R.string.day_sat),
            Calendar.SUNDAY to context.getString(R.string.day_sun)
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

    fun formatRuleTime(rule: QuietRule): String {
        val from = String.format(Locale.US, "%02d:%02d", rule.startHour, rule.startMinute)
        val to = String.format(Locale.US, "%02d:%02d", rule.endHour, rule.endMinute)
        val crossMidnight = rule.endHour * 60 + rule.endMinute <= rule.startHour * 60 + rule.startMinute
        return if (crossMidnight) "$from - $to ${context.getString(R.string.quiet_next_day)}" else "$from - $to"
    }

    fun closeDialog() { dialog = null }

    private fun toast(text: String, long: Boolean = false) =
        Toast.makeText(context, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}
