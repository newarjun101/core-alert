package com.arjun.core_alert.feature.settings.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
internal fun SoundTypeCard(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
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
                    selected = state.overrideSoundType == AlertSettingsRepository.SOUND_TYPE_RINGTONE,
                    label = stringResource(R.string.settings_sound_ringtone)
                ) { onAction(SettingsAction.SetOverrideSoundType(AlertSettingsRepository.SOUND_TYPE_RINGTONE)) }
                RadioRow(
                    selected = state.overrideSoundType == AlertSettingsRepository.SOUND_TYPE_NOTIFICATION,
                    label = stringResource(R.string.settings_sound_notification)
                ) { onAction(SettingsAction.SetOverrideSoundType(AlertSettingsRepository.SOUND_TYPE_NOTIFICATION)) }
            }
        }
    }
}
