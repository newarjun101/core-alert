package com.arjun.core_alert.feature.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arjun.core_alert.models.MessageApp
import com.arjun.core_alert.feature.R
import com.arjun.core_alert.models.VipContact
import com.arjun.core_alert.policy.ContactPhoneOption
import com.arjun.core_alert.policy.HomeWarning
import com.arjun.core_alert.ui.component.Overline
import com.arjun.core_alert.feature.home.component.CallModeSummaryButton
import com.arjun.core_alert.feature.home.component.ContactRow
import com.arjun.core_alert.feature.home.component.EmptyContactsCard
import com.arjun.core_alert.feature.home.component.HeroCard
import com.arjun.core_alert.feature.home.component.HomeDialogs
import com.arjun.core_alert.feature.home.component.MuteCard
import com.arjun.core_alert.feature.home.component.PermissionsCard
import com.arjun.core_alert.feature.home.component.WarningBanner

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
fun HomeScreen(state: HomeUiState, onAction: (HomeAction) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp
        )
    ) {
        if (state.warning != HomeWarning.NONE) {
            item(key = "warning") { WarningBanner(state.warning) }
        }
        item(key = "hero") { Spacer(Modifier.height(if (state.warning != HomeWarning.NONE) 12.dp else 0.dp)); HeroCard(state.serviceEnabled) { onAction(HomeAction.ServiceToggled(it)) } }
        item(key = "callmode") { Spacer(Modifier.height(12.dp)); CallModeSummaryButton(state.callModeSummary) { onAction(HomeAction.OpenCallModeSettings) } }
        item(key = "mute") { MuteCard(state.muteCountdown) { onAction(HomeAction.ToggleMute) } }
        item(key = "permissions") { Spacer(Modifier.height(12.dp)); PermissionsCard(state, onAction) }
        if (state.contacts.isNotEmpty()) {
            item(key = "contacts_header") {
                Overline(
                    text = stringResource(R.string.contacts_title),
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                )
            }
            itemsIndexed(state.contacts, key = { index, contact ->
                "$index:${contact.number}"
            }) { _, contact ->
                ContactRow(state, contact, onAction)
            }
        } else {
            item(key = "empty") { Spacer(Modifier.height(8.dp)); EmptyContactsCard() }
        }
    }

    HomeDialogs(state, onAction)
}
