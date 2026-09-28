package com.arjun.core_alert.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arjun.core_alert.feature.settings.component.AppearanceCard
import com.arjun.core_alert.feature.settings.component.BackupCard
import com.arjun.core_alert.feature.settings.component.MessageAlertsCard
import com.arjun.core_alert.feature.settings.component.QuietHoursCard
import com.arjun.core_alert.feature.settings.component.SettingsDialogs
import com.arjun.core_alert.feature.settings.component.SoundTypeCard
import com.arjun.core_alert.feature.settings.component.VolumeCard
import com.arjun.core_alert.models.QuietRule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface SettingsDialog {
    data object ExportPassword : SettingsDialog
    data object ImportConfirm : SettingsDialog
    data object ImportPassword : SettingsDialog
    data object AddQuietRule : SettingsDialog
    data class DeleteQuietRule(val index: Int, val rule: QuietRule) : SettingsDialog
}

@Composable
fun SettingsScreen(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            onAction(SettingsAction.ExportUri(it))
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            onAction(SettingsAction.ImportUri(it))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 28.dp)
    ) {
        AppearanceCard(state, onAction)
        if (state.messageAlertsSupported) {
            Spacer(Modifier.height(12.dp))
            MessageAlertsCard(state, onAction)
        }
        Spacer(Modifier.height(12.dp))
        VolumeCard(state, onAction)
        Spacer(Modifier.height(12.dp))
        SoundTypeCard(state, onAction)
        Spacer(Modifier.height(12.dp))
        QuietHoursCard(state, onAction)
        Spacer(Modifier.height(12.dp))
        BackupCard(onAction)
    }

    SettingsDialogs(state = state, onAction = onAction, onExport = { pass ->
        onAction(SettingsAction.ConfirmExportPassword(pass))
        exportLauncher.launch(exportFileName())
    },
        onImport = { importLauncher.launch(arrayOf("application/json", "*/*")) }
    )
}

private fun exportFileName(): String {
    val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    return "corealert-backup-$timestamp.json"
}
