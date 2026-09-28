package com.arjun.core_alert.feature.home

import android.Manifest
import android.content.res.Resources
import android.os.Build
import com.arjun.core_alert.feature.R
import com.arjun.core_alert.models.CallAlertMode
import com.arjun.core_alert.models.MessageAlertState
import com.arjun.core_alert.models.MessageApp
import com.arjun.core_alert.models.VipContact
import com.arjun.core_alert.policy.ContactPhoneOption
import com.arjun.core_alert.policy.HomeWarning

data class HomeUiState(
    val contacts: List<VipContact> = emptyList(),
    val serviceEnabled: Boolean = false,
    val runtimeGranted: Boolean = false,
    val dndGranted: Boolean = false,
    val contactsPermissionGranted: Boolean = false,
    val warning: HomeWarning = HomeWarning.NONE,
    val muteCountdown: String? = null,
    val callModeSummary: String = "",
    val callAlertMode: CallAlertMode = CallAlertMode.FIRST,
    val repeatWindowMinutes: Int = 5,
    val escalateCallVolume: Boolean = false,
    val volumePercent: Int = 50,
    val messageAlertsSupported: Boolean = false,
    val messageStates: Map<String, MessageAlertState> = emptyMap(),
    val dialog: HomeDialog? = null
) {
    fun messageState(contact: VipContact, app: MessageApp): MessageAlertState =
        messageStates[messageKey(contact, app)] ?: MessageAlertState.UNPAIRED
}

internal val runtimePermissions: List<String> = buildList {
    add(Manifest.permission.READ_PHONE_STATE)
    add(Manifest.permission.READ_CALL_LOG)
    add(Manifest.permission.READ_CONTACTS)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}

internal fun messageKey(contact: VipContact, app: MessageApp): String = "${contact.number}|${app.name}"

internal fun phoneOptionLabel(res: Resources, option: ContactPhoneOption): String =
    if (option.label.isBlank()) option.number
    else res.getString(R.string.contact_number_option, option.label, option.number)

internal fun messageAppName(res: Resources, app: MessageApp): String = res.getString(
    when (app) {
        MessageApp.WHATSAPP -> R.string.message_app_whatsapp
        MessageApp.GOOGLE_MESSAGES -> R.string.message_app_google_messages
        MessageApp.TELEGRAM -> R.string.message_app_telegram
        MessageApp.VIBER -> R.string.message_app_viber
    }
)

internal fun messageMenuTitle(res: Resources, state: MessageAlertState, app: MessageApp): String {
    val appName = messageAppName(res, app)
    return when (state) {
        MessageAlertState.UNPAIRED -> res.getString(R.string.message_pair_app, appName)
        MessageAlertState.PAIRING -> res.getString(R.string.message_pair_cancel_app, appName)
        MessageAlertState.PAIRED -> res.getString(R.string.message_unpair_app, appName)
    }
}
