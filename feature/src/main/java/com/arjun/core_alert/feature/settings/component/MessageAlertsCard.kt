package com.arjun.core_alert.feature.settings.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arjun.core_alert.AlertSettingsRepository
import com.arjun.core_alert.feature.R
import com.arjun.core_alert.ui.component.AppCard
import com.arjun.core_alert.ui.component.CardHeader
import com.arjun.core_alert.ui.component.RadioRow
import com.arjun.core_alert.ui.theme.coreAlertColors
import com.arjun.core_alert.feature.settings.SettingsAction
import com.arjun.core_alert.feature.settings.SettingsUiState

@Composable
internal fun MessageAlertsCard(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    val colors = coreAlertColors()
    val enabled = state.messageSoundEnabled
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
                ValuePill("${state.messageVolumePercent}%")
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
                    onCheckedChange = { onAction(SettingsAction.SetMessageSoundEnabled(it)) }
                )
            }
            VolumeSlider(
                value = state.messageVolumePercent,
                range = 5f..100f,
                steps = 18,
                enabled = enabled,
                onValueChange = { onAction(SettingsAction.SetMessageVolume(it)) },
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
                    selected = state.messageSoundType == AlertSettingsRepository.MESSAGE_SOUND_DEFAULT,
                    label = stringResource(R.string.message_sound_default)
                ) { onAction(SettingsAction.SetMessageSoundType(AlertSettingsRepository.MESSAGE_SOUND_DEFAULT)) }
                RadioRow(
                    selected = state.messageSoundType == AlertSettingsRepository.MESSAGE_SOUND_CONTACT,
                    label = stringResource(R.string.message_sound_contact)
                ) { onAction(SettingsAction.SetMessageSoundType(AlertSettingsRepository.MESSAGE_SOUND_CONTACT)) }
            }
        }
    }
}
