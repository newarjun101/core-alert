package com.arjun.core_alert

import com.arjun.core_alert.models.MessageAlertState
import com.arjun.core_alert.models.MessageApp
import com.arjun.core_alert.models.PendingMessagePairing
import com.arjun.core_alert.models.VipContact

interface MessageBindingRepository {

    val pending: PendingMessagePairing?

    fun beginPairing(number: String, app: MessageApp)

    fun cancelPairing()

    fun state(number: String, app: MessageApp): MessageAlertState

    fun bind(number: String, app: MessageApp, conversationHash: String, initialEventFingerprint: String)

    fun contactForConversation(
        app: MessageApp,
        conversationHash: String,
        contacts: List<VipContact>
    ): VipContact?

    fun isNewEvent(app: MessageApp, conversationHash: String, fingerprint: String): Boolean

    fun unpair(number: String, app: MessageApp)

    fun unpairAll(number: String)

    fun migrateNumber(oldNumber: String, newNumber: String)
}
