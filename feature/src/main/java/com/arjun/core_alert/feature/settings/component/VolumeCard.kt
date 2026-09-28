package com.arjun.core_alert.feature.settings.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arjun.core_alert.feature.R
import com.arjun.core_alert.feature.home.component.CallAlertSettingsContent
import com.arjun.core_alert.ui.component.AppCard
import com.arjun.core_alert.ui.component.CardHeader
import com.arjun.core_alert.feature.settings.SettingsAction
import com.arjun.core_alert.feature.settings.SettingsUiState

@Composable
internal fun VolumeCard(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardHeader(
                    iconRes = R.drawable.ic_volume,
                    title = stringResource(R.string.volume_label),
                    iconSize = 40.dp,
                    modifier = Modifier.weight(1f)
                )
                ValuePill("${state.volumePercent}%")
            }
            VolumeSlider(
                value = state.volumePercent,
                range = 25f..100f,
                steps = 14,
                enabled = true,
                onValueChange = { onAction(SettingsAction.SetVolume(it)) },
                modifier = Modifier.padding(top = 10.dp)
            )
            CallAlertSettingsContent(
                callAlertMode = state.callAlertMode,
                repeatWindowMinutes = state.repeatWindowMinutes,
                escalateCallVolume = state.escalateCallVolume,
                volumePercent = state.volumePercent,
                onCallAlertMode = { onAction(SettingsAction.SetCallAlertMode(it)) },
                onRepeatWindow = { onAction(SettingsAction.SetRepeatWindow(it)) },
                onEscalateVolume = { onAction(SettingsAction.SetEscalateVolume(it)) },
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
