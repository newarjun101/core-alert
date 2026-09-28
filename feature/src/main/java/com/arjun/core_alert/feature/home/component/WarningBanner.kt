package com.arjun.core_alert.feature.home.component

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arjun.core_alert.feature.R
import com.arjun.core_alert.policy.HomeWarning
import com.arjun.core_alert.ui.component.AppCard
import com.arjun.core_alert.ui.theme.coreAlertColors

@Composable
internal fun WarningBanner(warning: HomeWarning) {
    val colors = coreAlertColors()
    val context = LocalContext.current
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = colors.warningBg,
        borderColor = Color.Transparent,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(colors.warning.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_alert),
                    contentDescription = null,
                    tint = colors.warning,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = stringResource(
                    when (warning) {
                        HomeWarning.PERMISSIONS_REVOKED -> R.string.home_warn_perms_revoked
                        HomeWarning.AUTO_REVOKE -> R.string.home_warn_auto_revoke
                        HomeWarning.BATTERY_UNRESTRICTED_NEEDED -> R.string.home_warn_battery_unrestricted
                        HomeWarning.NONE -> R.string.home_warn_action_fix
                    }
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                color = colors.ink,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 13.sp
            )
            TextButtonTone(
                text = stringResource(R.string.home_warn_action_fix),
                color = colors.warning,
                onClick = {
                    if (warning == HomeWarning.BATTERY_UNRESTRICTED_NEEDED) {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    } else {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun TextButtonTone(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        color = color,
        style = MaterialTheme.typography.labelLarge
    )
}
