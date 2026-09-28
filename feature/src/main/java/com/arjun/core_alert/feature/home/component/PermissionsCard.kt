package com.arjun.core_alert.feature.home.component

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arjun.core_alert.feature.R
import com.arjun.core_alert.ui.component.AppCard
import com.arjun.core_alert.ui.component.Overline
import com.arjun.core_alert.ui.theme.coreAlertColors
import com.arjun.core_alert.feature.home.HomeUiState
import com.arjun.core_alert.feature.home.HomeAction
import com.arjun.core_alert.feature.home.runtimePermissions

@Composable
internal fun PermissionsCard(state: HomeUiState, onAction: (HomeAction) -> Unit) {
    val colors = coreAlertColors()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> onAction(HomeAction.PermissionResult(results)) }
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Overline(text = stringResource(R.string.permissions_title), modifier = Modifier.padding(bottom = 14.dp))
            PermissionRow(
                icon = R.drawable.ic_phone,
                label = stringResource(R.string.runtime_perm_label),
                granted = state.runtimeGranted,
                actionLabel = stringResource(R.string.authorize),
                onAction = { permissionLauncher.launch(runtimePermissions.toTypedArray()) }
            )
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.outline)
            )
            PermissionRow(
                icon = R.drawable.ic_bell,
                label = stringResource(R.string.dnd_perm_label),
                granted = state.dndGranted,
                actionLabel = stringResource(R.string.configure),
                onAction = { onAction(HomeAction.OpenDndDialog) }
            )
        }
    }
}

@Composable
private fun PermissionRow(
    icon: Int,
    label: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    val colors = coreAlertColors()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = colors.accent
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = label,
                color = colors.ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(if (granted) R.string.status_granted else R.string.status_missing),
                color = if (granted) colors.statusOk else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp
            )
        }
        if (granted) {
            Icon(
                painter = painterResource(R.drawable.ic_shield_check),
                contentDescription = stringResource(R.string.status_granted),
                tint = colors.statusOk,
                modifier = Modifier.size(26.dp)
            )
        } else {
            OutlinedButton(
                onClick = onAction,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, colors.outline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accent),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(text = actionLabel, fontSize = 12.sp)
            }
        }
    }
}
