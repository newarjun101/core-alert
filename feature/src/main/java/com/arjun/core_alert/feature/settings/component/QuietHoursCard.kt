package com.arjun.core_alert.feature.settings.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arjun.core_alert.AlertSettingsRepository
import com.arjun.core_alert.models.QuietRule
import com.arjun.core_alert.feature.R
import com.arjun.core_alert.ui.component.AppCard
import com.arjun.core_alert.ui.component.CardHeader
import com.arjun.core_alert.ui.theme.coreAlertColors
import com.arjun.core_alert.feature.settings.SettingsAction
import com.arjun.core_alert.feature.settings.SettingsUiState
import com.arjun.core_alert.feature.settings.formatRuleDays
import com.arjun.core_alert.feature.settings.formatRuleTime

@Composable
internal fun QuietHoursCard(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    val context = LocalContext.current
    val colors = coreAlertColors()
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            CardHeader(
                iconRes = R.drawable.ic_moon,
                title = stringResource(R.string.quiet_hours_title),
                subtitle = stringResource(R.string.quiet_hours_subtitle),
                iconSize = 40.dp
            )
            state.quietRules.forEachIndexed { index, rule ->
                QuietRuleRow(
                    rule = rule,
                    days = formatRuleDays(context.resources, rule),
                    time = formatRuleTime(context.resources, rule),
                    onDelete = { onAction(SettingsAction.OpenDeleteQuietRule(index)) }
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
                    enabled = state.quietRules.size < AlertSettingsRepository.MAX_QUIET_RULES,
                    onClick = { onAction(SettingsAction.OpenAddQuietRule) }
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
