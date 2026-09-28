package com.arjun.core_alert.feature.settings.component

import android.app.Activity
import androidx.annotation.ColorRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arjun.core_alert.models.AppLanguage
import com.arjun.core_alert.models.AppPalette
import com.arjun.core_alert.models.NightMode
import com.arjun.core_alert.feature.R
import com.arjun.core_alert.ui.component.AppCard
import com.arjun.core_alert.ui.component.CardHeader
import com.arjun.core_alert.ui.theme.coreAlertColors
import com.arjun.core_alert.feature.settings.SettingsAction
import com.arjun.core_alert.feature.settings.SettingsUiState

@Composable
internal fun AppearanceCard(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
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
                    selected = state.themePalette == AppPalette.INDACO,
                    modifier = Modifier.weight(1f).padding(end = 3.dp)
                ) { onAction(SettingsAction.SelectPalette(AppPalette.INDACO)) }
                SwatchEntry(
                    color = R.color.teal_primary,
                    label = stringResource(R.string.palette_teal),
                    selected = state.themePalette == AppPalette.TEAL,
                    modifier = Modifier.weight(1f).padding(horizontal = 3.dp)
                ) { onAction(SettingsAction.SelectPalette(AppPalette.TEAL)) }
                SwatchEntry(
                    color = R.color.argilla_primary,
                    label = stringResource(R.string.palette_argilla),
                    selected = state.themePalette == AppPalette.ARGILLA,
                    modifier = Modifier.weight(1f).padding(horizontal = 3.dp)
                ) { onAction(SettingsAction.SelectPalette(AppPalette.ARGILLA)) }
                SwatchEntry(
                    color = R.color.ardesia_primary,
                    label = stringResource(R.string.palette_ardesia),
                    selected = state.themePalette == AppPalette.ARDESIA,
                    modifier = Modifier.weight(1f).padding(start = 3.dp)
                ) { onAction(SettingsAction.SelectPalette(AppPalette.ARDESIA)) }
            }
            Text(
                text = stringResource(R.string.appearance_mode),
                modifier = Modifier.padding(top = 18.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp
            )
            ModeToggle(
                selected = state.nightMode,
                onSelect = { onAction(SettingsAction.SelectNightMode(it)) },
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
                selected = state.language,
                onSelect = { onAction(SettingsAction.SelectLanguage(it, activity)) },
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
