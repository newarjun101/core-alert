package com.arjun.core_alert

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.arjun.core_alert.models.MessageAlertState
import com.arjun.core_alert.models.MessageApp
import com.arjun.core_alert.models.VipContact

class VipMessageAlertsProvider(private val bindings: MessageBindingRepository) {

    val supported: Boolean = true
    val apps: List<MessageApp> = MessageApp.entries

    fun hasNotificationAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun state(contact: VipContact, app: MessageApp): MessageAlertState =
        bindings.state(contact.number, app)

    fun beginPairing(contact: VipContact, app: MessageApp) {
        bindings.beginPairing(contact.number, app)
    }

    fun cancelPairing() {
        bindings.cancelPairing()
    }

    fun unpair(contact: VipContact, app: MessageApp) {
        bindings.unpair(contact.number, app)
    }

    fun onContactChanged(oldNumber: String, newNumber: String) {
        bindings.migrateNumber(oldNumber, newNumber)
    }

    fun onContactRemoved(number: String) {
        bindings.unpairAll(number)
    }
}
