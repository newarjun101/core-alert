package com.arjun.core_alert.feature.settings

import android.app.Activity
import android.net.Uri
import com.arjun.core_alert.models.AppLanguage
import com.arjun.core_alert.models.AppPalette
import com.arjun.core_alert.models.CallAlertMode
import com.arjun.core_alert.models.NightMode
import com.arjun.core_alert.models.QuietRule

sealed interface SettingsAction {
    data class SelectPalette(val palette: AppPalette) : SettingsAction
    data class SelectNightMode(val mode: NightMode) : SettingsAction
    data class SelectLanguage(val language: AppLanguage, val activity: Activity?) : SettingsAction
    data class SetVolume(val value: Int) : SettingsAction
    data class SetMessageVolume(val value: Int) : SettingsAction
    data class SetMessageSoundEnabled(val enabled: Boolean) : SettingsAction
    data class SetMessageSoundType(val type: Int) : SettingsAction
    data class SetOverrideSoundType(val type: Int) : SettingsAction
    data class SetCallAlertMode(val mode: CallAlertMode) : SettingsAction
    data class SetRepeatWindow(val minutes: Int) : SettingsAction
    data class SetEscalateVolume(val enabled: Boolean) : SettingsAction
    data object OpenExportPassword : SettingsAction
    data class ConfirmExportPassword(val password: String) : SettingsAction
    data class ExportUri(val uri: Uri) : SettingsAction
    data object OpenImportConfirm : SettingsAction
    data class ImportUri(val uri: Uri) : SettingsAction
    data class DecryptPendingImport(val password: String) : SettingsAction
    data object OpenAddQuietRule : SettingsAction
    data class ConfirmAddQuietRule(val rule: QuietRule) : SettingsAction
    data class OpenDeleteQuietRule(val index: Int) : SettingsAction
    data class ConfirmDeleteQuietRule(val index: Int) : SettingsAction
    data object CloseDialog : SettingsAction
}
