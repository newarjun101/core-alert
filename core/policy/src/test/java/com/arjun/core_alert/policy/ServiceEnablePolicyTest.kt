package com.arjun.core_alert.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceEnablePolicyTest {

    @Test fun allFourGranted_canEnable() {
        assertTrue(ServiceEnablePolicy.canEnable(phone = true, callLog = true, dnd = true, notif = true))
    }

    @Test fun phoneMissing_cannotEnable() {
        assertFalse(ServiceEnablePolicy.canEnable(phone = false, callLog = true, dnd = true, notif = true))
    }

    @Test fun callLogMissing_cannotEnable() {
        assertFalse(ServiceEnablePolicy.canEnable(phone = true, callLog = false, dnd = true, notif = true))
    }

    @Test fun dndMissing_cannotEnable() {
        assertFalse(ServiceEnablePolicy.canEnable(phone = true, callLog = true, dnd = false, notif = true))
    }

    @Test fun notifMissing_cannotEnable() {
        assertFalse(ServiceEnablePolicy.canEnable(phone = true, callLog = true, dnd = true, notif = false))
    }

    @Test fun allFourMissing_cannotEnable() {
        assertFalse(ServiceEnablePolicy.canEnable(phone = false, callLog = false, dnd = false, notif = false))
    }
}
