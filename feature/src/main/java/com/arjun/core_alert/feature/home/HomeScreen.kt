package com.arjun.core_alert.feature.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arjun.core_alert.AlertSettingsRepository
import com.arjun.core_alert.models.CallAlertMode
import com.arjun.core_alert.models.MessageAlertState
import com.arjun.core_alert.models.MessageApp
import com.arjun.core_alert.feature.R
import com.arjun.core_alert.models.VipContact
import com.arjun.core_alert.policy.ContactPhoneOption
import com.arjun.core_alert.policy.HomeWarning
import com.arjun.core_alert.ui.component.AppCard
import com.arjun.core_alert.ui.component.ListDialog
import com.arjun.core_alert.ui.component.MultiChoiceDialog
import com.arjun.core_alert.ui.component.Overline
import com.arjun.core_alert.ui.component.SingleChoiceDialog
import com.arjun.core_alert.ui.component.SoftPill
import com.arjun.core_alert.ui.component.SosDialog
import com.arjun.core_alert.ui.component.SosTextField
import com.arjun.core_alert.ui.theme.coreAlertColors
import java.util.Locale

sealed interface HomeDialog {
    data object AddChoice : HomeDialog
    data class ContactForm(
        @StringRes val title: Int,
        @StringRes val confirm: Int,
        val name: String = "",
        val number: String = "",
        val editIndex: Int? = null
    ) : HomeDialog

    data class ContactNumbers(val name: String, val options: List<ContactPhoneOption>) : HomeDialog
    data class RemoveContact(val contact: VipContact) : HomeDialog
    data class ContactCallMode(val contact: VipContact) : HomeDialog
    data object CallModeSettings : HomeDialog
    data object MuteHours : HomeDialog
    data object DndExplain : HomeDialog
    data class MessageAccess(val contact: VipContact, val app: MessageApp) : HomeDialog
    data class MessagePair(val contact: VipContact, val app: MessageApp) : HomeDialog
}

@Composable
fun HomeScreen(home: HomeViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp
        )
    ) {
        if (home.warning != HomeWarning.NONE) {
            item(key = "warning") { WarningBanner(home) }
        }
        item(key = "hero") { Spacer(Modifier.height(if (home.warning != HomeWarning.NONE) 12.dp else 0.dp)); HeroCard(home) }
        item(key = "callmode") { Spacer(Modifier.height(12.dp)); CallModeSummaryButton(home) }
        item(key = "mute") { MuteCard(home) }
        item(key = "permissions") { Spacer(Modifier.height(12.dp)); PermissionsCard(home) }
        if (home.contacts.isNotEmpty()) {
            item(key = "contacts_header") {
                Overline(
                    text = stringResource(R.string.contacts_title),
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                )
            }
            itemsIndexed(home.contacts, key = { index, contact ->
                "$index:${contact.number}"
            }) { _, contact ->
                ContactRow(home, contact)
            }
        } else {
            item(key = "empty") { Spacer(Modifier.height(8.dp)); EmptyContactsCard() }
        }
    }

    HomeDialogs(home)
}

@Composable
private fun WarningBanner(home: HomeViewModel) {
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
                    when (home.warning) {
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
                    if (home.warning == HomeWarning.BATTERY_UNRESTRICTED_NEEDED) {
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

@Composable
private fun HeroCard(home: HomeViewModel) {
    val colors = coreAlertColors()
    val active = home.serviceEnabled
    val heroBg = if (active) colors.heroBg else colors.heroOffBg
    val stateColor = if (active) colors.heroOn else colors.heroOffOn
    val labelColor = if (active) colors.heroFaded else colors.heroOffMuted

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(heroBg)
    ) {
        if (active) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0x00000000), Color(0x33000000))
                        )
                    )
            )
        }
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeroBell(active = active)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.home_monitoring_label),
                        color = labelColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = stringResource(
                            if (active) R.string.home_service_state_on else R.string.home_service_state_off
                        ),
                        color = stateColor,
                        style = MaterialTheme.typography.displaySmall
                    )
                    if (!active) {
                        Text(
                            text = stringResource(R.string.home_service_hint_off),
                            modifier = Modifier.padding(top = 6.dp),
                            color = colors.heroOffMuted,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 13.sp
                        )
                    }
                }
                Switch(
                    checked = active,
                    onCheckedChange = { home.onServiceToggled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0x8AFFFFFF),
                        checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = Color(0xFFC7CEF5),
                        uncheckedTrackColor = colors.outlineStrong,
                        uncheckedBorderColor = Color.Transparent
                    )
                )
            }
            if (active) {
                Row(
                    modifier = Modifier.padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HeroChip(stringResource(R.string.hero_silent))
                    HeroChip(stringResource(R.string.hero_vibrate))
                    HeroChip(stringResource(R.string.hero_dnd))
                }
            }
        }
    }
}

@Composable
private fun HeroBell(active: Boolean) {
    val colors = coreAlertColors()
    val transition = rememberInfiniteTransition(label = "pulse")
    val progress1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring1"
    )
    val progress2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(1600, StartOffsetType.Delay)
        ),
        label = "ring2"
    )

    Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        if (active) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawPulseRing(progress1)
                drawPulseRing(progress2)
            }
        }
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(Color(0x29FFFFFF), RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (active) Color.White else colors.heroOffBadgeBg,
                        RoundedCornerShape(50)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(if (active) R.drawable.ic_bell else R.drawable.ic_bell_off),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (active) colors.heroBg else colors.heroOffBadgeIcon
                )
            }
        }
    }
}

private fun DrawScope.drawPulseRing(progress: Float) {
    val scale = 1.15f + (1.9f - 1.15f) * progress
    val alpha = 0.6f * (1f - progress)
    val radius = size.minDimension / 2f * 0.62f * scale
    val stroke = 0.9f * (size.width / 24f)
    drawCircle(
        color = Color.White.copy(alpha = alpha),
        radius = radius,
        style = Stroke(width = stroke, cap = StrokeCap.Round)
    )
}

@Composable
private fun HeroChip(text: String) {
    Row(
        modifier = Modifier
            .background(Color(0x24FFFFFF), RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = Color.White
        )
        Spacer(Modifier.width(5.dp))
        Text(text = text, color = Color.White, style = MaterialTheme.typography.labelMedium, fontSize = 12.sp)
    }
}

@Composable
private fun CallModeSummaryButton(home: HomeViewModel) {
    val colors = coreAlertColors()
    OutlinedButton(
        onClick = { home.openCallModeSettings() },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, colors.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text = home.callModeSummary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MuteCard(home: HomeViewModel) {
    val colors = coreAlertColors()
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { home.toggleMute() }
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(colors.warning.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_bell_off),
                        contentDescription = null,
                        tint = colors.warning,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = stringResource(
                            if (home.muteCountdown != null) R.string.mute_button_cancel else R.string.mute_button
                        ),
                        color = colors.ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.mute_subtitle),
                        color = colors.inkSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_chevron),
                    contentDescription = null,
                    tint = colors.inkMuted,
                    modifier = Modifier.size(24.dp)
                )
            }
            home.muteCountdown?.let { countdown ->
                Text(
                    text = countdown,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                        .background(colors.warningBg, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    color = colors.warning,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PermissionsCard(home: HomeViewModel) {
    val colors = coreAlertColors()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> home.onPermissionResult(results) }
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Overline(text = stringResource(R.string.permissions_title), modifier = Modifier.padding(bottom = 14.dp))
            PermissionRow(
                icon = R.drawable.ic_phone,
                label = stringResource(R.string.runtime_perm_label),
                granted = home.runtimeGranted,
                actionLabel = stringResource(R.string.authorize),
                onAction = { permissionLauncher.launch(home.runtimePermissions) }
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
                granted = home.dndGranted,
                actionLabel = stringResource(R.string.configure),
                onAction = { home.openDndDialog() }
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

@Composable
private fun ContactRow(home: HomeViewModel, contact: VipContact) {
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
                    home = home,
                    contact = contact,
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false }
                )
            }
        }
    }
}

@Composable
private fun ContactMenu(
    home: HomeViewModel,
    contact: VipContact,
    expanded: Boolean,
    onDismiss: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_edit)) },
            onClick = {
                onDismiss()
                home.dialog = HomeDialog.ContactForm(
                    title = R.string.edit_contact_title,
                    confirm = R.string.btn_save,
                    name = contact.name,
                    number = contact.number,
                    editIndex = home.contacts.indexOfFirst { it.number == contact.number }
                )
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.call_mode_title)) },
            onClick = {
                onDismiss()
                home.dialog = HomeDialog.ContactCallMode(contact)
            }
        )
        if (home.messageAlerts.supported) {
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
                    text = { Text(home.messageMenuTitle(contact, app)) },
                    onClick = {
                        onDismiss()
                        home.onMessageAlertTap(contact, app)
                    }
                )
            }
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_delete)) },
            onClick = {
                onDismiss()
                home.dialog = HomeDialog.RemoveContact(contact)
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

@Composable
private fun EmptyContactsCard() {
    val colors = coreAlertColors()
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 28.dp, end = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_user),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = colors.accent
                )
            }
            Text(
                text = stringResource(R.string.contacts_empty_title),
                modifier = Modifier.padding(top = 16.dp),
                color = colors.ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.contacts_empty_body),
                modifier = Modifier.padding(top = 6.dp),
                color = colors.inkSecondary,
                fontSize = 13.5.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
            SoftPill(
                text = stringResource(R.string.contacts_empty_hint),
                modifier = Modifier.padding(top = 14.dp)
            )
        }
    }
}

@Composable
private fun HomeDialogs(home: HomeViewModel) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> home.onPermissionResult(results) }
    val contactPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri -> uri?.let(home::onContactPicked) }

    when (val d = home.dialog) {
        null -> Unit
        HomeDialog.AddChoice -> ListDialog(
            title = stringResource(R.string.add_choice_title),
            options = listOf(
                stringResource(R.string.choice_from_contacts),
                stringResource(R.string.choice_manual)
            ),
            onPick = { which ->
                home.dialog = null
                if (which == 0) {
                    if (home.needsContactsPermission()) {
                        permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                    } else {
                        contactPickerLauncher.launch(null)
                    }
                } else home.showManualAdd()
            },
            onDismiss = { home.closeDialog() }
        )

        is HomeDialog.ContactForm -> ContactFormDialog(home, d)
        is HomeDialog.ContactNumbers -> ContactNumbersDialog(home, d)
        is HomeDialog.RemoveContact -> SosDialog(
            title = stringResource(R.string.remove_contact_title),
            onDismiss = { home.closeDialog() },
            confirmLabel = stringResource(R.string.btn_remove),
            onConfirm = {
                home.deleteContact(d.contact)
                true
            }
        ) {
            Text(
                text = stringResource(R.string.remove_contact_msg, d.contact.name, d.contact.number),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        is HomeDialog.ContactCallMode -> {
            val options = listOf(
                stringResource(R.string.call_mode_inherit),
                stringResource(R.string.call_mode_first),
                stringResource(R.string.call_mode_second, home.repeatWindowMinutes())
            )
            val modes = listOf(CallAlertMode.INHERIT, CallAlertMode.FIRST, CallAlertMode.SECOND)
            SingleChoiceDialog(
                title = stringResource(R.string.call_mode_contact, d.contact.name),
                options = options,
                selected = modes.indexOf(d.contact.callAlertMode).coerceAtLeast(0),
                onPick = { index -> home.setContactCallMode(d.contact, modes[index]) },
                onDismiss = { home.closeDialog() }
            )
        }

        HomeDialog.CallModeSettings -> CallModeSettingsDialog(home)
        HomeDialog.MuteHours -> MuteHoursDialog(home)
        HomeDialog.DndExplain -> SosDialog(
            title = stringResource(R.string.dnd_dialog_title),
            onDismiss = { home.closeDialog() },
            confirmLabel = stringResource(R.string.btn_open_settings),
            onConfirm = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                true
            }
        ) {
            Text(
                text = stringResource(R.string.dnd_dialog_msg),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        is HomeDialog.MessageAccess -> SosDialog(
            title = stringResource(R.string.message_access_title),
            onDismiss = { home.closeDialog() },
            confirmLabel = stringResource(R.string.btn_open_settings),
            onConfirm = {
                home.beginMessagePairing(d.contact, d.app)
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                false
            }
        ) {
            Text(
                text = stringResource(R.string.message_access_message),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        is HomeDialog.MessagePair -> SosDialog(
            title = stringResource(R.string.message_pair_title, home.messageAppName(d.app)),
            onDismiss = { home.closeDialog() },
            confirmLabel = stringResource(R.string.btn_continue),
            cancelLabel = stringResource(R.string.btn_cancel),
            onConfirm = { true },
            onCancel = {
                home.cancelMessagePairing(d.contact, d.app)
                home.closeDialog()
            }
        ) {
            Text(
                text = if (d.app == MessageApp.GOOGLE_MESSAGES) {
                    stringResource(R.string.message_pair_google_message, d.contact.name)
                } else {
                    stringResource(R.string.message_pair_message, home.messageAppName(d.app), d.contact.name)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
