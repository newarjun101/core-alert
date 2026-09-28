package com.arjun.core_alert.feature.settings

import android.app.Activity
import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SettingsViewModel(
    private val context: Application,
    private val settings: AlertSettingsRepository,
    private val theme: ThemeRepository,
    private val contactRepository: ContactRepository,
    private val onContactsChanged: () -> Unit,
    private val messageAlerts: VipMessageAlertsProvider
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private var pendingExportPassword: String? = null
    private var pendingImportText: String? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        setState {
            it.copy(
                volumePercent = settings.volumePercent,
                messageVolumePercent = settings.messageVolumePercent,
                messageSoundEnabled = settings.messageSoundEnabled,
                messageSoundType = settings.messageSoundType,
                overrideSoundType = settings.overrideSoundType,
                themePalette = theme.palette,
                nightMode = theme.nightMode,
                language = LanguageManager.current(context),
                quietRules = settings.getQuietRules(),
                messageAlertsSupported = messageAlerts.supported,
                callAlertMode = settings.callAlertMode,
                repeatWindowMinutes = settings.repeatCallWindowMinutes,
                escalateCallVolume = settings.escalateCallVolume
            )
        }
    }

    fun handleAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.SelectPalette -> selectPalette(action.palette)
            is SettingsAction.SelectNightMode -> selectNightMode(action.mode)
            is SettingsAction.SelectLanguage -> selectLanguage(action.language, action.activity)
            is SettingsAction.SetVolume -> setVolume(action.value)
            is SettingsAction.SetMessageVolume -> setMessageVolume(action.value)
            is SettingsAction.SetMessageSoundEnabled -> updateMessageSoundEnabled(action.enabled)
            is SettingsAction.SetMessageSoundType -> updateMessageSoundType(action.type)
            is SettingsAction.SetOverrideSoundType -> updateOverrideSoundType(action.type)
            is SettingsAction.SetCallAlertMode -> {
                settings.setCallAlertMode(action.mode)
                setState { it.copy(callAlertMode = action.mode) }
            }
            is SettingsAction.SetRepeatWindow -> {
                settings.setRepeatCallWindowMinutes(action.minutes)
                setState { it.copy(repeatWindowMinutes = action.minutes) }
            }
            is SettingsAction.SetEscalateVolume -> {
                settings.setEscalateCallVolume(action.enabled)
                setState { it.copy(escalateCallVolume = action.enabled) }
            }
            SettingsAction.OpenExportPassword -> openDialog(SettingsDialog.ExportPassword)
            is SettingsAction.ConfirmExportPassword -> pendingExportPassword = action.password
            is SettingsAction.ExportUri -> onExportUri(action.uri)
            SettingsAction.OpenImportConfirm -> openDialog(SettingsDialog.ImportConfirm)
            is SettingsAction.ImportUri -> onImportUri(action.uri)
            is SettingsAction.DecryptPendingImport -> decryptPendingImport(action.password)
            SettingsAction.OpenAddQuietRule -> openAddQuietRule()
            is SettingsAction.ConfirmAddQuietRule -> addQuietRule(action.rule)
            is SettingsAction.OpenDeleteQuietRule -> openDeleteQuietRule(action.index)
            is SettingsAction.ConfirmDeleteQuietRule -> deleteQuietRule(action.index)
            SettingsAction.CloseDialog -> closeDialog()
        }
    }

    private fun setState(transform: (SettingsUiState) -> SettingsUiState) {
        _state.update { current -> transform(current) }
    }

    private fun openDialog(dialog: SettingsDialog) {
        setState { it.copy(dialog = dialog) }
    }

    private fun closeDialog() {
        setState { it.copy(dialog = null) }
    }

    private fun selectPalette(palette: AppPalette) {
        if (palette == theme.palette) return
        theme.setPalette(palette)
        setState { it.copy(themePalette = palette) }
        ThemeManager.applyPalette(palette)
    }

    private fun selectNightMode(mode: NightMode) {
        if (mode == theme.nightMode) return
        theme.setNightMode(mode)
        setState { it.copy(nightMode = mode) }
        ThemeManager.applyNightMode(mode)
    }

    private fun selectLanguage(selected: AppLanguage, activity: Activity?) {
        if (selected == state.value.language) return
        LanguageManager.apply(context, selected, activity)
        setState { it.copy(language = LanguageManager.current(context)) }
    }

    private fun setVolume(value: Int) {
        setState { it.copy(volumePercent = value) }
        settings.setVolumePercent(value)
    }

    private fun setMessageVolume(value: Int) {
        setState { it.copy(messageVolumePercent = value) }
        settings.setMessageVolumePercent(value)
    }

    private fun updateMessageSoundEnabled(enabled: Boolean) {
        setState { it.copy(messageSoundEnabled = enabled) }
        settings.setMessageSoundEnabled(enabled)
        if (!enabled) CallMonitorService.getInstance()?.suspendMessageAlert()
    }

    private fun updateMessageSoundType(type: Int) {
        setState { it.copy(messageSoundType = type) }
        settings.setMessageSoundType(type)
    }

    private fun updateOverrideSoundType(type: Int) {
        setState { it.copy(overrideSoundType = type) }
        settings.setOverrideSoundType(type)
    }

    private fun onExportUri(uri: Uri) {
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

    private fun onImportUri(uri: Uri) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { isr ->
                isr.bufferedReader(Charsets.UTF_8).readText()
            } ?: return

            if (!ConfigCrypto.isEncryptedEnvelope(text)) {
                toast(context.getString(R.string.backup_import_legacy_rejected), long = true)
                return
            }
            pendingImportText = text
            openDialog(SettingsDialog.ImportPassword)
        } catch (e: Exception) {
            toast(context.getString(R.string.backup_import_failed), long = true)
        }
    }

    private fun decryptPendingImport(password: String) {
        val text = pendingImportText ?: return
        pendingImportText = null
        Thread {
            val plaintext = ConfigCrypto.decrypt(text, password)
            val config = plaintext?.let { ConfigExporter.import(it) }
            mainHandler.post {
                closeDialog()
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

        setState {
            it.copy(
                volumePercent = config.volumePercent,
                overrideSoundType = config.overrideSoundType,
                messageVolumePercent = config.messageVolumePercent,
                messageSoundEnabled = config.messageSoundEnabled,
                messageSoundType = config.messageSoundType,
                quietRules = settings.getQuietRules(),
                callAlertMode = config.callAlertMode,
                repeatWindowMinutes = config.repeatCallWindowMinutes,
                escalateCallVolume = config.escalateCallVolume
            )
        }
        onContactsChanged()

        toast(context.getString(R.string.backup_import_success))

        if (settings.isServiceEnabled) {
            CallMonitorService.stop(context)
            CallMonitorService.start(context)
        }
    }

    private fun openAddQuietRule() {
        if (state.value.quietRules.size >= AlertSettingsRepository.MAX_QUIET_RULES) {
            toast(context.getString(R.string.quiet_max_rules))
        } else {
            openDialog(SettingsDialog.AddQuietRule)
        }
    }

    private fun addQuietRule(rule: QuietRule) {
        if (!QuietRulePolicy.isValid(rule.days, rule.startHour, rule.startMinute, rule.endHour, rule.endMinute)) {
            toast(context.getString(R.string.quiet_invalid_rule))
            return
        }
        setState { it.copy(quietRules = it.quietRules + rule, dialog = null) }
        settings.saveQuietRules(state.value.quietRules)
        if (settings.isInQuietPeriod()) CallMonitorService.getInstance()?.suspendActiveAlert()
    }

    private fun openDeleteQuietRule(index: Int) {
        val rule = state.value.quietRules.getOrNull(index) ?: return
        openDialog(SettingsDialog.DeleteQuietRule(index, rule))
    }

    private fun deleteQuietRule(index: Int) {
        setState { current -> current.copy(quietRules = current.quietRules.filterIndexed { i, _ -> i != index }) }
        settings.saveQuietRules(state.value.quietRules)
    }

    private fun toast(text: String, long: Boolean = false) =
        Toast.makeText(context, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}
