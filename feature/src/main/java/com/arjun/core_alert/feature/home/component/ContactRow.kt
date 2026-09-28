package com.arjun.core_alert.feature.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arjun.core_alert.models.MessageApp
import com.arjun.core_alert.feature.R
import com.arjun.core_alert.models.VipContact
import com.arjun.core_alert.ui.component.AppCard
import com.arjun.core_alert.ui.theme.coreAlertColors
import java.util.Locale
import com.arjun.core_alert.feature.home.HomeUiState
import com.arjun.core_alert.feature.home.HomeAction
import com.arjun.core_alert.feature.home.messageMenuTitle

@Composable
internal fun ContactRow(state: HomeUiState, contact: VipContact, onAction: (HomeAction) -> Unit) {
    val colors = coreAlertColors()
    var menuOpen by remember { mutableStateOf(false) }
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contactInitials(contact.name),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            ) {
                Text(
                    text = contact.name,
                    color = colors.inkHeading,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = contact.number,
                    color = colors.inkSecondary,
                    fontSize = 12.5.sp
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = stringResource(R.string.more_icon_desc),
                        tint = colors.inkSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                ContactMenu(
                    state = state,
                    contact = contact,
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    onAction = onAction
                )
            }
        }
    }
}

@Composable
private fun ContactMenu(
    state: HomeUiState,
    contact: VipContact,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (HomeAction) -> Unit
) {
    val context = LocalContext.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_edit)) },
            onClick = {
                onDismiss()
                onAction(HomeAction.EditContact(contact))
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.call_mode_title)) },
            onClick = {
                onDismiss()
                onAction(HomeAction.OpenContactCallMode(contact))
            }
        )
        if (state.messageAlertsSupported) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.message_alerts_menu),
                        color = coreAlertColors().inkSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp
                    )
                },
                enabled = false,
                onClick = {}
            )
            MessageApp.entries.forEach { app ->
                DropdownMenuItem(
                    text = {
                        Text(
                            messageMenuTitle(
                                context.resources,
                                state.messageState(contact, app),
                                app
                            )
                        )
                    },
                    onClick = {
                        onDismiss()
                        onAction(HomeAction.MessageAlertTap(contact, app))
                    }
                )
            }
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_delete)) },
            onClick = {
                onDismiss()
                onAction(HomeAction.OpenRemoveContact(contact))
            }
        )
    }
}

private fun contactInitials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    val initials = when {
        parts.size >= 2 -> "${parts.first().first()}${parts.last().first()}"
        parts.size == 1 -> parts.first().take(2)
        else -> "?"
    }
    return initials.uppercase(Locale.getDefault())
}
