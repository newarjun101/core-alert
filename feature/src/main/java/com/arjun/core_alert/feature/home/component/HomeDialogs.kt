package com.arjun.core_alert.feature.home.component

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arjun.core_alert.models.CallAlertMode
import com.arjun.core_alert.feature.R
import com.arjun.core_alert.policy.RepeatCallPolicy
import com.arjun.core_alert.ui.component.OutlinedChoiceButton
import com.arjun.core_alert.ui.component.Overline
import com.arjun.core_alert.ui.component.RadioRow
import com.arjun.core_alert.ui.component.SosDialog
import com.arjun.core_alert.ui.component.SosTextField
import com.arjun.core_alert.ui.theme.coreAlertColors
import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.arjun.core_alert.models.MessageApp
import com.arjun.core_alert.ui.component.ListDialog
import com.arjun.core_alert.ui.component.MultiChoiceDialog
import com.arjun.core_alert.ui.component.SingleChoiceDialog
import com.arjun.core_alert.feature.home.HomeDialog
import com.arjun.core_alert.feature.home.HomeUiState
import com.arjun.core_alert.feature.home.HomeAction
import com.arjun.core_alert.feature.home.phoneOptionLabel
import com.arjun.core_alert.feature.home.messageAppName

@Composable
internal fun ContactFormDialog(d: HomeDialog.ContactForm, onAction: (HomeAction) -> Unit) {
    var name by remember(d) { mutableStateOf(d.name) }
    var number by remember(d) { mutableStateOf(d.number) }
    SosDialog(
        title = stringResource(d.title),
        onDismiss = { onAction(HomeAction.CloseDialog) },
        confirmLabel = stringResource(d.confirm),
        onConfirm = {
            onAction(HomeAction.SaveContact(name, number, d.editIndex))
            false
        }
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
internal fun ContactNumbersDialog(d: HomeDialog.ContactNumbers, onAction: (HomeAction) -> Unit) {
    val context = LocalContext.current
    var checked by remember(d) { mutableStateOf(List(d.options.size) { true }) }
    com.arjun.core_alert.ui.component.MultiChoiceDialog(
        title = stringResource(R.string.contact_numbers_title, d.name),
        options = d.options.map { phoneOptionLabel(context.resources, it) },
        checked = checked,
        onCheckedChange = { checked = it },
        onConfirm = {
            val selected = d.options.filterIndexed { index, _ -> checked[index] }
            onAction(HomeAction.SaveSelectedNumbers(d.name, selected))
            false
        },
        onDismiss = { onAction(HomeAction.CloseDialog) }
    )
}

@Composable
internal fun MuteHoursDialog(onAction: (HomeAction) -> Unit) {
    var hours by remember { mutableStateOf(1) }
    SosDialog(
        title = stringResource(R.string.mute_dialog_title),
        onDismiss = { onAction(HomeAction.CloseDialog) },
        confirmLabel = stringResource(R.string.btn_save),
        onConfirm = {
            onAction(HomeAction.ActivateMute(hours))
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
    callAlertMode: CallAlertMode,
    repeatWindowMinutes: Int,
    escalateCallVolume: Boolean,
    volumePercent: Int,
    onCallAlertMode: (CallAlertMode) -> Unit,
    onRepeatWindow: (Int) -> Unit,
    onEscalateVolume: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = coreAlertColors()
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
                selected = callAlertMode != CallAlertMode.SECOND,
                label = stringResource(R.string.call_mode_first)
            ) {
                onCallAlertMode(CallAlertMode.FIRST)
            }
            RadioRow(
                selected = callAlertMode == CallAlertMode.SECOND,
                label = stringResource(R.string.call_mode_second, repeatWindowMinutes)
            ) {
                onCallAlertMode(CallAlertMode.SECOND)
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedChoiceButton(
            text = stringResource(R.string.call_window_value, repeatWindowMinutes),
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
                checked = escalateCallVolume,
                onCheckedChange = { enabled -> onEscalateVolume(enabled) }
            )
        }
        Text(
            text = if (volumePercent >= 50) {
                stringResource(R.string.call_escalate_high_volume)
            } else {
                stringResource(R.string.call_escalate_preview, volumePercent, (volumePercent * 2).coerceAtMost(100))
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
            selected = values.indexOf(repeatWindowMinutes).coerceAtLeast(0),
            onPick = { index -> onRepeatWindow(values[index]) },
            onDismiss = { showWindowPicker = false }
        )
    }
}

/** Home screen dialog wrapper around [CallAlertSettingsContent]. */
@Composable
internal fun CallModeSettingsDialog(state: HomeUiState, onAction: (HomeAction) -> Unit) {
    SosDialog(
        title = stringResource(R.string.call_mode_title),
        onDismiss = { onAction(HomeAction.CloseDialog) },
        confirmLabel = stringResource(android.R.string.ok),
        onConfirm = { true },
        onCancel = { onAction(HomeAction.CloseDialog) }
    ) {
        CallAlertSettingsContent(
            callAlertMode = state.callAlertMode,
            repeatWindowMinutes = state.repeatWindowMinutes,
            escalateCallVolume = state.escalateCallVolume,
            volumePercent = state.volumePercent,
            onCallAlertMode = { onAction(HomeAction.SetCallAlertMode(it)) },
            onRepeatWindow = { onAction(HomeAction.SetRepeatWindow(it)) },
            onEscalateVolume = { onAction(HomeAction.SetEscalateVolume(it)) }
        )
    }
}

@Composable
internal fun HomeDialogs(state: HomeUiState, onAction: (HomeAction) -> Unit) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> onAction(HomeAction.PermissionResult(results)) }
    val contactPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri -> uri?.let { onAction(HomeAction.ContactPicked(it)) } }

    when (val d = state.dialog) {
        null -> Unit
        HomeDialog.AddChoice -> ListDialog(
            title = stringResource(R.string.add_choice_title),
            options = listOf(
                stringResource(R.string.choice_from_contacts),
                stringResource(R.string.choice_manual)
            ),
            onPick = { which ->
                onAction(HomeAction.CloseDialog)
                if (which == 0) {
                    if (!state.contactsPermissionGranted) {
                        permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                    } else {
                        contactPickerLauncher.launch(null)
                    }
                } else {
                    onAction(HomeAction.ShowManualAdd)
                }
            },
            onDismiss = { onAction(HomeAction.CloseDialog) }
        )

        is HomeDialog.ContactForm -> ContactFormDialog(d, onAction)
        is HomeDialog.ContactNumbers -> ContactNumbersDialog(d, onAction)
        is HomeDialog.RemoveContact -> SosDialog(
            title = stringResource(R.string.remove_contact_title),
            onDismiss = { onAction(HomeAction.CloseDialog) },
            confirmLabel = stringResource(R.string.btn_remove),
            onConfirm = {
                onAction(HomeAction.ConfirmRemoveContact(d.contact))
                true
            }
        ) {
            Text(
                text = stringResource(R.string.remove_contact_msg, d.contact.name, d.contact.number),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        is HomeDialog.ContactCallMode -> {
            val options = listOf(
                stringResource(R.string.call_mode_inherit),
                stringResource(R.string.call_mode_first),
                stringResource(R.string.call_mode_second, state.repeatWindowMinutes)
            )
            val modes = listOf(CallAlertMode.INHERIT, CallAlertMode.FIRST, CallAlertMode.SECOND)
            SingleChoiceDialog(
                title = stringResource(R.string.call_mode_contact, d.contact.name),
                options = options,
                selected = modes.indexOf(d.contact.callAlertMode).coerceAtLeast(0),
                onPick = { index -> onAction(HomeAction.SetContactCallMode(d.contact, modes[index])) },
                onDismiss = { onAction(HomeAction.CloseDialog) }
            )
        }

        HomeDialog.CallModeSettings -> CallModeSettingsDialog(state, onAction)
        HomeDialog.MuteHours -> MuteHoursDialog(onAction)
        HomeDialog.DndExplain -> SosDialog(
            title = stringResource(R.string.dnd_dialog_title),
            onDismiss = { onAction(HomeAction.CloseDialog) },
            confirmLabel = stringResource(R.string.btn_open_settings),
            onConfirm = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                true
            }
        ) {
            Text(
                text = stringResource(R.string.dnd_dialog_msg),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        is HomeDialog.MessageAccess -> SosDialog(
            title = stringResource(R.string.message_access_title),
            onDismiss = { onAction(HomeAction.CloseDialog) },
            confirmLabel = stringResource(R.string.btn_open_settings),
            onConfirm = {
                onAction(HomeAction.BeginMessagePairing(d.contact, d.app))
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                false
            }
        ) {
            Text(
                text = stringResource(R.string.message_access_message),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        is HomeDialog.MessagePair -> SosDialog(
            title = stringResource(R.string.message_pair_title, messageAppName(context.resources, d.app)),
            onDismiss = { onAction(HomeAction.CloseDialog) },
            confirmLabel = stringResource(R.string.btn_continue),
            cancelLabel = stringResource(R.string.btn_cancel),
            onConfirm = { true },
            onCancel = {
                onAction(HomeAction.CancelMessagePairing(d.contact, d.app))
                onAction(HomeAction.CloseDialog)
            }
        ) {
            Text(
                text = if (d.app == MessageApp.GOOGLE_MESSAGES) {
                    stringResource(R.string.message_pair_google_message, d.contact.name)
                } else {
                    stringResource(
                        R.string.message_pair_message,
                        messageAppName(context.resources, d.app),
                        d.contact.name
                    )
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
