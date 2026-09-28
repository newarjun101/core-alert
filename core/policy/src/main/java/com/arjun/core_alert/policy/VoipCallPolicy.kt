package com.arjun.core_alert.policy

import com.arjun.core_alert.models.MessageApp

object VoipCallPolicy {
    const val CATEGORY_CALL = "call"

    fun isCallNotification(app: MessageApp?, category: String?, hasFullScreenIntent: Boolean): Boolean =
        app != null && (category == CATEGORY_CALL || hasFullScreenIntent)

    fun shouldRing(callerNumbers: List<String>, matchedVip: Boolean): Boolean =
        callerNumbers.isEmpty() || matchedVip
}
