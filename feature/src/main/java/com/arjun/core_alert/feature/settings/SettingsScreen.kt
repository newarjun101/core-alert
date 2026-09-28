package com.arjun.core_alert.feature.settings

import android.app.Activity
import android.content.DialogInterface
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arjun.core_alert.AlertSettingsRepository
import com.arjun.core_alert.AppConfig
import com.arjun.core_alert.models.AppLanguage
import com.arjun.core_alert.models.AppPalette
import com.arjun.core_alert.CallMonitorService
import com.arjun.core_alert.ConfigCrypto
import com.arjun.core_alert.ConfigExporter
import com.arjun.core_alert.ContactRepository
import com.arjun.core_alert.models.NightMode
import com.arjun.core_alert.models.QuietRule
import com.arjun.core_alert.policy.QuietRulePolicy
import com.arjun.core_alert.feature.R
import com.arjun.core_alert.feature.home.CallAlertSettingsContent
import com.arjun.core_alert.ThemeManager
import com.arjun.core_alert.ThemeRepository
import com.arjun.core_alert.VipMessageAlertsProvider
import com.arjun.core_alert.ui.component.AppCard
import com.arjun.core_alert.ui.component.CardHeader
import com.arjun.core_alert.ui.component.Overline
import com.arjun.core_alert.ui.component.RadioRow
import com.arjun.core_alert.ui.component.SosDialog
import com.arjun.core_alert.ui.component.SosTextField
import com.arjun.core_alert.ui.component.TimeStepper
import com.arjun.core_alert.ui.theme.coreAlertColors
import java.text.SimpleDateFormat
import java.util.Calendar
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
fun SettingsScreen(vm: SettingsViewModel) {
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(vm::onExportUri) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::onImportUri) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 28.dp)
    ) {
        AppearanceCard(vm)
        if (vm.messageAlerts.supported) {
            Spacer(Modifier.height(12.dp))
            MessageAlertsCard(vm)
        }
        Spacer(Modifier.height(12.dp))
        VolumeCard(vm)
        Spacer(Modifier.height(12.dp))
        SoundTypeCard(vm)
        Spacer(Modifier.height(12.dp))
        QuietHoursCard(vm)
        Spacer(Modifier.height(12.dp))
        BackupCard(vm)
    }

    SettingsDialogs(
        vm = vm,
        onExport = { pass -> exportLauncher.launch(vm.beginExport(pass)) },
        onImport = { importLauncher.launch(arrayOf("application/json", "*/*")) }
    )
}

@Composable
private fun AppearanceCard(vm: SettingsViewModel) {
    val activity = LocalContext.current as? Activity
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            CardHeader(iconRes = R.drawable.ic_sun, title = stringResource(R.string.appearance_title), iconSize = 40.dp)
            Text(
                text = stringResource(R.string.appearance_palette),
                modifier = Modifier.padding(top = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp
            )
            Row(modifier = Modifier.padding(top = 10.dp)) {
                SwatchEntry(
                    color = R.color.indaco_primary,
                    label = stringResource(R.string.palette_indaco),
                    selected = vm.themePalette == AppPalette.INDACO,
                    modifier = Modifier.weight(1f).padding(end = 3.dp)
                ) { vm.selectPalette(AppPalette.INDACO) }
                SwatchEntry(
                    color = R.color.teal_primary,
                    label = stringResource(R.string.palette_teal),
                    selected = vm.themePalette == AppPalette.TEAL,
                    modifier = Modifier.weight(1f).padding(horizontal = 3.dp)
                ) { vm.selectPalette(AppPalette.TEAL) }
                SwatchEntry(
                    color = R.color.argilla_primary,
                    label = stringResource(R.string.palette_argilla),
                    selected = vm.themePalette == AppPalette.ARGILLA,
                    modifier = Modifier.weight(1f).padding(horizontal = 3.dp)
                ) { vm.selectPalette(AppPalette.ARGILLA) }
                SwatchEntry(
                    color = R.color.ardesia_primary,
                    label = stringResource(R.string.palette_ardesia),
                    selected = vm.themePalette == AppPalette.ARDESIA,
                    modifier = Modifier.weight(1f).padding(start = 3.dp)
                ) { vm.selectPalette(AppPalette.ARDESIA) }
            }
            Text(
                text = stringResource(R.string.appearance_mode),
                modifier = Modifier.padding(top = 18.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp
            )
            ModeToggle(
                selected = vm.nightMode,
                onSelect = { vm.selectNightMode(it) },
                modifier = Modifier.padding(top = 10.dp)
            )
            Text(
                text = stringResource(R.string.language_label),
                modifier = Modifier.padding(top = 18.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp
            )
            LanguageToggle(
                selected = vm.language,
                onSelect = { vm.selectLanguage(it, activity) },
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
private fun SwatchEntry(
    @ColorRes color: Int,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = coreAlertColors()
    val border = if (selected) MaterialTheme.colorScheme.primary else colors.outline
    Column(
        modifier = modifier
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = border,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(colorResource(color), CircleShape)
                .border(1.dp, Color(0x22000000), CircleShape)
        )
        Text(
            text = label,
            modifier = Modifier.padding(top = 6.dp),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun ModeToggle(selected: NightMode, onSelect: (NightMode) -> Unit, modifier: Modifier = Modifier) {
    val colors = coreAlertColors()
    val modes = listOf(
        NightMode.FOLLOW_SYSTEM to stringResource(R.string.mode_system),
        NightMode.LIGHT to stringResource(R.string.mode_light),
        NightMode.DARK to stringResource(R.string.mode_dark)
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceAlt, RoundedCornerShape(14.dp))
            .padding(4.dp)
    ) {
        modes.forEach { (mode, label) ->
            val isActive = selected == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        RoundedCornerShape(11.dp)
                    )
                    .clickable { onSelect(mode) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else colors.inkSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun LanguageToggle(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = coreAlertColors()
    val languages = listOf(
        AppLanguage.SYSTEM to stringResource(R.string.language_system),
        AppLanguage.ENGLISH to stringResource(R.string.language_english),
        AppLanguage.MYANMAR to stringResource(R.string.language_myanmar)
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceAlt, RoundedCornerShape(14.dp))
            .padding(4.dp)
    ) {
        languages.forEach { (language, label) ->
            val isActive = selected == language
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        RoundedCornerShape(11.dp)
                    )
                    .clickable { onSelect(language) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else colors.inkSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun MessageAlertsCard(vm: SettingsViewModel) {
    val colors = coreAlertColors()
    val enabled = vm.messageSoundEnabled
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardHeader(
                    iconRes = R.drawable.ic_bell,
                    title = stringResource(R.string.message_settings_title),
                    subtitle = stringResource(R.string.message_settings_desc),
                    iconSize = 40.dp,
                    modifier = Modifier.weight(1f)
                )
                ValuePill("${vm.messageVolumePercent}%")
            }
            Row(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.message_sound_enabled),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = { vm.updateMessageSoundEnabled(it) }
                )
            }
            VolumeSlider(
                value = vm.messageVolumePercent,
                range = 5f..100f,
                steps = 18,
                enabled = enabled,
                onValueChange = { vm.setMessageVolume(it) },
                modifier = Modifier.padding(top = 10.dp)
            )
            Column(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .background(colors.surfaceAlt, RoundedCornerShape(14.dp))
                    .padding(8.dp)
                    .let { if (enabled) it else it.alpha(0.45f) }
            ) {
                RadioRow(
                    selected = vm.messageSoundType == AlertSettingsRepository.MESSAGE_SOUND_DEFAULT,
                    label = stringResource(R.string.message_sound_default)
                ) { vm.updateMessageSoundType(AlertSettingsRepository.MESSAGE_SOUND_DEFAULT) }
                RadioRow(
                    selected = vm.messageSoundType == AlertSettingsRepository.MESSAGE_SOUND_CONTACT,
                    label = stringResource(R.string.message_sound_contact)
                ) { vm.updateMessageSoundType(AlertSettingsRepository.MESSAGE_SOUND_CONTACT) }
            }
        }
    }
}

@Composable
private fun VolumeCard(vm: SettingsViewModel) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardHeader(
                    iconRes = R.drawable.ic_volume,
                    title = stringResource(R.string.volume_label),
                    iconSize = 40.dp,
                    modifier = Modifier.weight(1f)
                )
                ValuePill("${vm.volumePercent}%")
            }
            VolumeSlider(
                value = vm.volumePercent,
                range = 25f..100f,
                steps = 14,
                enabled = true,
                onValueChange = { vm.setVolume(it) },
                modifier = Modifier.padding(top = 10.dp)
            )
            CallAlertSettingsContent(
                settings = vm.settings,
                onChanged = {},
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun ValuePill(text: String) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun VolumeSlider(
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = coreAlertColors()
    Slider(
        value = value.toFloat(),
        onValueChange = { onValueChange(it.toInt()) },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        valueRange = range,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = colors.accent,
            activeTrackColor = colors.accent,
            inactiveTrackColor = colors.trackInactive
        )
    )
}

@Composable
private fun SoundTypeCard(vm: SettingsViewModel) {
    val colors = coreAlertColors()
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            CardHeader(iconRes = R.drawable.ic_bell, title = stringResource(R.string.settings_sound_title), iconSize = 40.dp)
            Column(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .background(colors.surfaceAlt, RoundedCornerShape(14.dp))
                    .padding(8.dp)
            ) {
                RadioRow(
                    selected = vm.overrideSoundType == AlertSettingsRepository.SOUND_TYPE_RINGTONE,
                    label = stringResource(R.string.settings_sound_ringtone)
                ) { vm.updateOverrideSoundType(AlertSettingsRepository.SOUND_TYPE_RINGTONE) }
                RadioRow(
                    selected = vm.overrideSoundType == AlertSettingsRepository.SOUND_TYPE_NOTIFICATION,
                    label = stringResource(R.string.settings_sound_notification)
                ) { vm.updateOverrideSoundType(AlertSettingsRepository.SOUND_TYPE_NOTIFICATION) }
            }
        }
    }
}

@Composable
private fun QuietHoursCard(vm: SettingsViewModel) {
    val colors = coreAlertColors()
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            CardHeader(
                iconRes = R.drawable.ic_moon,
                title = stringResource(R.string.quiet_hours_title),
                subtitle = stringResource(R.string.quiet_hours_subtitle),
                iconSize = 40.dp
            )
            vm.quietRules.forEachIndexed { index, rule ->
                QuietRuleRow(
                    rule = rule,
                    days = vm.formatRuleDays(rule),
                    time = vm.formatRuleTime(rule),
                    onDelete = { vm.dialog = SettingsDialog.DeleteQuietRule(index, rule) }
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButtonLike(
                    text = stringResource(R.string.quiet_add_rule),
                    icon = R.drawable.ic_plus,
                    enabled = vm.quietRules.size < AlertSettingsRepository.MAX_QUIET_RULES,
                    onClick = { vm.openAddQuietRule() }
                )
            }
        }
    }
}

@Composable
private fun QuietRuleRow(rule: QuietRule, days: String, time: String, onDelete: () -> Unit) {
    val colors = coreAlertColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 6.dp)
            .background(colors.surfaceAlt, RoundedCornerShape(20.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_clock),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = colors.accent
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
        ) {
            Text(text = days, color = colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                text = time,
                modifier = Modifier.padding(top = 2.dp),
                color = colors.inkSecondary,
                fontSize = 13.sp
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                painter = painterResource(R.drawable.ic_trash),
                contentDescription = stringResource(R.string.btn_remove),
                tint = colors.danger,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun TextButtonLike(text: String, icon: Int, enabled: Boolean, onClick: () -> Unit) {
    val colors = coreAlertColors()
    Row(
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (enabled) colors.accent else colors.inkMuted,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else colors.inkMuted,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun BackupCard(vm: SettingsViewModel) {
    val colors = coreAlertColors()
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            CardHeader(iconRes = R.drawable.ic_download, title = stringResource(R.string.backup_title), iconSize = 40.dp)
            Button(
                onClick = { vm.openExportPasswordDialog() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colorResource(R.color.on_primary)
                )
            ) {
                Icon(painter = painterResource(R.drawable.ic_upload), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(text = stringResource(R.string.backup_export), fontSize = 14.sp)
            }
            OutlinedButton(
                onClick = { vm.openImportConfirm() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, colors.outline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_download),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colors.accent
                )
                Spacer(Modifier.width(8.dp))
                Text(text = stringResource(R.string.backup_import), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun SettingsDialogs(
    vm: SettingsViewModel,
    onExport: (String) -> Unit,
    onImport: () -> Unit
) {
    when (val current = vm.dialog) {
        null -> Unit
        SettingsDialog.ExportPassword -> ExportPasswordDialog(vm, onExport)
        SettingsDialog.ImportConfirm -> SosDialog(
            title = stringResource(R.string.backup_import_confirm_title),
            onDismiss = { vm.closeDialog() },
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
                onDismiss = { vm.closeDialog() },
                confirmLabel = stringResource(R.string.btn_save),
                onConfirm = {
                    vm.decryptPendingImport(password)
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

        SettingsDialog.AddQuietRule -> AddQuietRuleDialog(vm)
        is SettingsDialog.DeleteQuietRule -> SosDialog(
            title = stringResource(R.string.quiet_delete_title),
            onDismiss = { vm.closeDialog() },
            confirmLabel = stringResource(R.string.btn_remove),
            onConfirm = {
                vm.deleteQuietRule(current.index)
                true
            }
        ) {
            Text(
                text = stringResource(
                    R.string.quiet_delete_msg,
                    vm.formatRuleDays(current.rule),
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
private fun ExportPasswordDialog(vm: SettingsViewModel, onExport: (String) -> Unit) {
    val ctx = LocalContext.current
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    SosDialog(
        title = stringResource(R.string.backup_export_confirm_title),
        onDismiss = { vm.closeDialog() },
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
private fun AddQuietRuleDialog(vm: SettingsViewModel) {
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
        onDismiss = { vm.closeDialog() },
        confirmLabel = stringResource(R.string.btn_save),
        onConfirm = {
            vm.addQuietRule(
                QuietRule(selected, fromHour, fromMinute, toHour, toMinute)
            )
        }
    ) {
        Overline(text = stringResource(R.string.quiet_days_label))
        Row(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            dayOrder.take(4).forEach { (day, res) ->
                DayChip(
                    label = stringResource(res),
                    selected = day in selected,
                    modifier = Modifier.weight(1f)
                ) {
                    selected = if (day in selected) selected - day else selected + day
                }
            }
        }
        Row(
            modifier = Modifier
                .padding(top = 4.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            dayOrder.drop(4).forEach { (day, res) ->
                DayChip(
                    label = stringResource(res),
                    selected = day in selected,
                    modifier = Modifier.weight(1f)
                ) {
                    selected = if (day in selected) selected - day else selected + day
                }
            }
            Spacer(Modifier.weight(1f))
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
