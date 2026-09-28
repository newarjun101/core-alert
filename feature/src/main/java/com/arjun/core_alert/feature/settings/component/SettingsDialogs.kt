package com.arjun.core_alert.feature.settings.component

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arjun.core_alert.models.QuietRule
import com.arjun.core_alert.feature.R
import com.arjun.core_alert.ui.component.Overline
import com.arjun.core_alert.ui.component.SosDialog
import com.arjun.core_alert.ui.component.SosTextField
import com.arjun.core_alert.ui.component.TimeStepper
import com.arjun.core_alert.ui.theme.coreAlertColors
import java.util.Calendar
import java.util.Locale
import com.arjun.core_alert.feature.settings.SettingsAction
import com.arjun.core_alert.feature.settings.SettingsUiState
import com.arjun.core_alert.feature.settings.SettingsDialog
import com.arjun.core_alert.feature.settings.formatRuleDays

@Composable
internal fun SettingsDialogs(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onExport: (String) -> Unit,
    onImport: () -> Unit
) {
    val context = LocalContext.current
    when (val current = state.dialog) {
        null -> Unit
        SettingsDialog.ExportPassword -> ExportPasswordDialog(onAction, onExport)
        SettingsDialog.ImportConfirm -> SosDialog(
            title = stringResource(R.string.backup_import_confirm_title),
            onDismiss = { onAction(SettingsAction.CloseDialog) },
            confirmLabel = stringResource(R.string.btn_continue),
            onConfirm = {
                onImport()
                true
            }
        ) {
            Text(
                text = stringResource(R.string.backup_import_confirm),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        SettingsDialog.ImportPassword -> {
            var password by remember { mutableStateOf("") }
            SosDialog(
                title = stringResource(R.string.backup_import_password_title),
                onDismiss = { onAction(SettingsAction.CloseDialog) },
                confirmLabel = stringResource(R.string.btn_save),
                onConfirm = {
                    onAction(SettingsAction.DecryptPendingImport(password))
                    false
                }
            ) {
                Text(
                    text = stringResource(R.string.backup_import_password_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                SosTextField(
                    value = password,
                    onValueChange = { password = it },
                    hint = stringResource(R.string.backup_password_hint),
                    password = true
                )
            }
        }

        SettingsDialog.AddQuietRule -> AddQuietRuleDialog(onAction)
        is SettingsDialog.DeleteQuietRule -> SosDialog(
            title = stringResource(R.string.quiet_delete_title),
            onDismiss = { onAction(SettingsAction.CloseDialog) },
            confirmLabel = stringResource(R.string.btn_remove),
            onConfirm = {
                onAction(SettingsAction.ConfirmDeleteQuietRule(current.index))
                true
            }
        ) {
            Text(
                text = stringResource(
                    R.string.quiet_delete_msg,
                    formatRuleDays(context.resources, current.rule),
                    String.format(Locale.US, "%02d:%02d", current.rule.startHour, current.rule.startMinute),
                    String.format(Locale.US, "%02d:%02d", current.rule.endHour, current.rule.endMinute)
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ExportPasswordDialog(onAction: (SettingsAction) -> Unit, onExport: (String) -> Unit) {
    val ctx = LocalContext.current
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    SosDialog(
        title = stringResource(R.string.backup_export_confirm_title),
        onDismiss = { onAction(SettingsAction.CloseDialog) },
        confirmLabel = stringResource(R.string.backup_export),
        onConfirm = {
            when {
                pass.isEmpty() -> {
                    android.widget.Toast.makeText(ctx, R.string.backup_password_empty, Toast.LENGTH_SHORT).show()
                    false
                }
                pass != confirm -> {
                    android.widget.Toast.makeText(ctx, R.string.backup_password_mismatch, Toast.LENGTH_SHORT).show()
                    false
                }
                else -> {
                    onExport(pass)
                    true
                }
            }
        }
    ) {
        Text(
            text = stringResource(R.string.backup_export_confirm),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))
        SosTextField(
            value = pass,
            onValueChange = { pass = it },
            hint = stringResource(R.string.backup_password_hint),
            password = true
        )
        Spacer(Modifier.height(12.dp))
        SosTextField(
            value = confirm,
            onValueChange = { confirm = it },
            hint = stringResource(R.string.backup_password_confirm_hint),
            password = true
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AddQuietRuleDialog(onAction: (SettingsAction) -> Unit) {
    val dayOrder = listOf(
        Calendar.MONDAY to R.string.day_mon,
        Calendar.TUESDAY to R.string.day_tue,
        Calendar.WEDNESDAY to R.string.day_wed,
        Calendar.THURSDAY to R.string.day_thu,
        Calendar.FRIDAY to R.string.day_fri,
        Calendar.SATURDAY to R.string.day_sat,
        Calendar.SUNDAY to R.string.day_sun
    )
    var selected by remember { mutableStateOf(setOf(Calendar.MONDAY)) }
    var fromHour by remember { mutableStateOf(9) }
    var fromMinute by remember { mutableStateOf(0) }
    var toHour by remember { mutableStateOf(18) }
    var toMinute by remember { mutableStateOf(0) }

    val crossMidnight = toHour * 60 + toMinute <= fromHour * 60 + fromMinute

    SosDialog(
        title = stringResource(R.string.quiet_new_rule_title),
        onDismiss = { onAction(SettingsAction.CloseDialog) },
        confirmLabel = stringResource(R.string.btn_save),
        onConfirm = {
            onAction(
                SettingsAction.ConfirmAddQuietRule(
                    QuietRule(selected, fromHour, fromMinute, toHour, toMinute)
                )
            )
            false
        }
    ) {
        Overline(text = stringResource(R.string.quiet_days_label))
        FlowRow(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            dayOrder.forEach { (day, res) ->
                DayChip(
                    label = stringResource(res),
                    selected = day in selected
                ) {
                    selected = if (day in selected) selected - day else selected + day
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        TimeStepper(
            label = stringResource(R.string.quiet_from),
            hour = fromHour,
            minute = fromMinute,
            onChange = { h, m -> fromHour = h; fromMinute = m }
        )
        Spacer(Modifier.height(6.dp))
        TimeStepper(
            label = stringResource(R.string.quiet_to),
            hour = toHour,
            minute = toMinute,
            onChange = { h, m -> toHour = h; toMinute = m }
        )
        if (crossMidnight) {
            Text(
                text = stringResource(R.string.quiet_next_day),
                modifier = Modifier.padding(top = 8.dp),
                color = coreAlertColors().warning,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun DayChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = coreAlertColors()
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(text = label, fontSize = 11.sp, maxLines = 1)
        },
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = colors.surfaceAlt,
            labelColor = colors.inkSecondary,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else colors.outline
        )
    )
}
