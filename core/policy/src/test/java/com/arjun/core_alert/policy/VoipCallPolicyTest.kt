package com.arjun.core_alert.policy

import com.arjun.core_alert.models.MessageApp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoipCallPolicyTest {

    @Test
    fun matchesCallNotificationsOfSupportedApps() {
        assertTrue(VoipCallPolicy.isCallNotification(MessageApp.VIBER, "call", false))
        assertTrue(VoipCallPolicy.isCallNotification(MessageApp.WHATSAPP, null, true))
        assertFalse(VoipCallPolicy.isCallNotification(MessageApp.VIBER, "msg", false))
        assertFalse(VoipCallPolicy.isCallNotification(MessageApp.VIBER, null, false))
        assertFalse(VoipCallPolicy.isCallNotification(null, "call", false))
    }

    @Test
    fun ringsForUnknownCallersAndVipNumbers() {
        assertTrue(VoipCallPolicy.shouldRing(emptyList(), matchedVip = false))
        assertTrue(VoipCallPolicy.shouldRing(listOf("+393331234567"), matchedVip = true))
        assertFalse(VoipCallPolicy.shouldRing(listOf("+39333000000"), matchedVip = false))
    }
}
