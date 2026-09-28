package com.arjun.core_alert.policy

import java.util.Calendar
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietRulePolicyTest {

    @Test
    fun acceptsRulesWithAtLeastOneDayAndDifferentTimes() {
        assertTrue(
            QuietRulePolicy.isValid(
                days = setOf(Calendar.MONDAY),
                startHour = 22,
                startMinute = 0,
                endHour = 6,
                endMinute = 0
            )
        )
    }

    @Test
    fun rejectsEmptyDaysAndZeroDurationRules() {
        assertFalse(
            QuietRulePolicy.isValid(
                days = emptySet(),
                startHour = 9,
                startMinute = 0,
                endHour = 18,
                endMinute = 0
            )
        )
        assertFalse(
            QuietRulePolicy.isValid(
                days = setOf(Calendar.MONDAY),
                startHour = 9,
                startMinute = 0,
                endHour = 9,
                endMinute = 0
            )
        )
    }
}
