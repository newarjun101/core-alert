package com.arjun.core_alert.feature.home

import android.net.Uri
import com.arjun.core_alert.models.CallAlertMode
import com.arjun.core_alert.models.MessageApp
import com.arjun.core_alert.models.VipContact
import com.arjun.core_alert.policy.ContactPhoneOption

sealed interface HomeAction {
    data object Refresh : HomeAction
    data class ServiceToggled(val checked: Boolean) : HomeAction
    data class PermissionResult(val results: Map<String, Boolean>) : HomeAction
    data object OpenDndDialog : HomeAction
    data object ToggleMute : HomeAction
    data class ActivateMute(val hours: Int) : HomeAction
    data object OpenAddChoice : HomeAction
    data object ShowManualAdd : HomeAction
    data class EditContact(val contact: VipContact) : HomeAction
    data class OpenContactCallMode(val contact: VipContact) : HomeAction
    data class OpenRemoveContact(val contact: VipContact) : HomeAction
    data class ConfirmRemoveContact(val contact: VipContact) : HomeAction
    data class SaveContact(val name: String, val number: String, val editIndex: Int?) : HomeAction
    data class SaveSelectedNumbers(val name: String, val selected: List<ContactPhoneOption>) : HomeAction
    data class SetContactCallMode(val contact: VipContact, val mode: CallAlertMode) : HomeAction
    data object OpenCallModeSettings : HomeAction
    data class SetCallAlertMode(val mode: CallAlertMode) : HomeAction
    data class SetRepeatWindow(val minutes: Int) : HomeAction
    data class SetEscalateVolume(val enabled: Boolean) : HomeAction
    data class ContactPicked(val uri: Uri) : HomeAction
    data class MessageAlertTap(val contact: VipContact, val app: MessageApp) : HomeAction
    data class BeginMessagePairing(val contact: VipContact, val app: MessageApp) : HomeAction
    data class CancelMessagePairing(val contact: VipContact, val app: MessageApp) : HomeAction
    data object CloseDialog : HomeAction
}
