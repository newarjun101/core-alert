package com.arjun.core_alert.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arjun.core_alert.AlertSettingsRepository
import com.arjun.core_alert.models.CallAlertMode
import com.arjun.core_alert.feature.R
import com.arjun.core_alert.policy.RepeatCallPolicy
import com.arjun.core_alert.ui.component.OutlinedChoiceButton
import com.arjun.core_alert.ui.component.Overline
import com.arjun.core_alert.ui.component.RadioRow
import com.arjun.core_alert.ui.component.SosDialog
import com.arjun.core_alert.ui.component.SosTextField
import com.arjun.core_alert.ui.theme.coreAlertColors

@Composable
internal fun ContactFormDialog(home: HomeViewModel, d: HomeDialog.ContactForm) {
    var name by remember(d) { mutableStateOf(d.name) }
    var number by remember(d) { mutableStateOf(d.number) }
    SosDialog(
        title = stringResource(d.title),
        onDismiss = { home.closeDialog() },
        confirmLabel = stringResource(d.confirm),
        onConfirm = { home.saveContact(name, number, d.editIndex) }
    ) {
        SosTextField(
            value = name,
            onValueChange = { name = it },
            hint = stringResource(R.string.dialog_name_hint)
        )
        Spacer(Modifier.height(12.dp))
        SosTextField(
            value = number,
            onValueChange = { number = it },
            hint = stringResource(R.string.dialog_phone_hint)
        )
    }
}

@Composable
internal fun ContactNumbersDialog(home: HomeViewModel, d: HomeDialog.ContactNumbers) {
    var checked by remember(d) { mutableStateOf(List(d.options.size) { true }) }
    com.arjun.core_alert.ui.component.MultiChoiceDialog(
        title = stringResource(R.string.contact_numbers_title, d.name),
        options = d.options.map { home.phoneOptionLabel(it) },
        checked = checked,
        onCheckedChange = { checked = it },
        onConfirm = {
            val selected = d.options.filterIndexed { index, _ -> checked[index] }
            home.saveSelectedNumbers(d.name, selected)
        },
        onDismiss = { home.closeDialog() }
    )
}

@Composable
internal fun MuteHoursDialog(home: HomeViewModel) {
    var hours by remember { mutableStateOf(1) }
    SosDialog(
        title = stringResource(R.string.mute_dialog_title),
        onDismiss = { home.closeDialog() },
        confirmLabel = stringResource(R.string.btn_save),
        onConfirm = {
            home.activateMute(hours)
            true
        }
    ) {
        Text(
            text = stringResource(R.string.mute_dialog_message),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StepperButton("-") { hours = (hours - 1).coerceAtLeast(1) }
            Text(
                text = stringResource(R.string.mute_activated, hours),
                style = MaterialTheme.typography.titleMedium,
                color = coreAlertColors().accent
            )
            StepperButton("+") { hours = (hours + 1).coerceAtMost(12) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "1 – 12 h",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun StepperButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = symbol, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

/** Shared call-alert controls: used inline in Settings and inside the Home dialog. */
@Composable
internal fun CallAlertSettingsContent(
    settings: AlertSettingsRepository,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = coreAlertColors()
    var mode by remember { mutableStateOf(settings.callAlertMode) }
    var window by remember { mutableStateOf(settings.repeatCallWindowMinutes) }
    var escalate by remember { mutableStateOf(settings.escalateCallVolume) }
    var showWindowPicker by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Overline(text = stringResource(R.string.call_mode_title))
        Column(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .background(colors.surfaceAlt, RoundedCornerShape(14.dp))
                .padding(8.dp)
        ) {
            RadioRow(
                selected = mode != CallAlertMode.SECOND,
                label = stringResource(R.string.call_mode_first)
            ) {
                mode = CallAlertMode.FIRST
                settings.setCallAlertMode(mode)
                onChanged()
            }
            RadioRow(
                selected = mode == CallAlertMode.SECOND,
                label = stringResource(R.string.call_mode_second, window)
            ) {
                mode = CallAlertMode.SECOND
                settings.setCallAlertMode(mode)
                onChanged()
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedChoiceButton(
            text = stringResource(R.string.call_window_value, window),
            onClick = { showWindowPicker = true }
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.call_escalate_title),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )
            Switch(
                checked = escalate,
                onCheckedChange = { enabled ->
                    escalate = enabled
                    settings.setEscalateCallVolume(enabled)
                    onChanged()
                }
            )
        }
        val base = settings.volumePercent
        Text(
            text = if (base >= 50) {
                stringResource(R.string.call_escalate_high_volume)
            } else {
                stringResource(R.string.call_escalate_preview, base, (base * 2).coerceAtMost(100))
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.call_mode_help),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            lineHeight = 17.sp
        )
    }

    if (showWindowPicker) {
        val values = RepeatCallPolicy.windowOptions
        com.arjun.core_alert.ui.component.SingleChoiceDialog(
            title = stringResource(R.string.call_window_title),
            options = values.map { stringResource(R.string.call_window_value, it) },
            selected = values.indexOf(window).coerceAtLeast(0),
            onPick = { index ->
                window = values[index]
                settings.setRepeatCallWindowMinutes(values[index])
                onChanged()
            },
            onDismiss = { showWindowPicker = false }
        )
    }
}

/** Home screen dialog wrapper around [CallAlertSettingsContent]. */
@Composable
internal fun CallModeSettingsDialog(home: HomeViewModel) {
    SosDialog(
        title = stringResource(R.string.call_mode_title),
        onDismiss = { home.closeDialog() },
        confirmLabel = stringResource(android.R.string.ok),
        onConfirm = { true },
        onCancel = { home.closeDialog() }
    ) {
        CallAlertSettingsContent(
            settings = home.settings,
            onChanged = { home.onCallModeSettingsChanged() }
        )
    }
}
