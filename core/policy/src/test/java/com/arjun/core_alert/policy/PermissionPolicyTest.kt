package com.arjun.core_alert.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionPolicyTest {
    @Test fun allGranted_noneMissing() {
        assertTrue(PermissionPolicy.criticalMissing(callLogOk = true, phoneStateOk = true, dndOk = true).isEmpty())
    }
    @Test fun reportsEachMissing() {
        val missing = PermissionPolicy.criticalMissing(callLogOk = false, phoneStateOk = true, dndOk = false)
        assertEquals(listOf("READ_CALL_LOG", "DND"), missing)
    }
}
