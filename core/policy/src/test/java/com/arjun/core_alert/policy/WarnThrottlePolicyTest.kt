package com.arjun.core_alert.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WarnThrottlePolicyTest {
    @Test fun warns_afterInterval() = assertTrue(WarnThrottlePolicy.shouldWarn(0L, 24L * 60 * 60 * 1000))
    @Test fun throttled_withinInterval() = assertFalse(WarnThrottlePolicy.shouldWarn(0L, 1000L))
}
