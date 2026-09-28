package com.arjun.core_alert.feature.settings.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arjun.core_alert.feature.R
import com.arjun.core_alert.ui.component.AppCard
import com.arjun.core_alert.ui.component.CardHeader
import com.arjun.core_alert.ui.theme.coreAlertColors
import com.arjun.core_alert.feature.settings.SettingsAction

@Composable
internal fun BackupCard(onAction: (SettingsAction) -> Unit) {
    val colors = coreAlertColors()
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            CardHeader(iconRes = R.drawable.ic_download, title = stringResource(R.string.backup_title), iconSize = 40.dp)
            Button(
                onClick = { onAction(SettingsAction.OpenExportPassword) },
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
                onClick = { onAction(SettingsAction.OpenImportConfirm) },
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
