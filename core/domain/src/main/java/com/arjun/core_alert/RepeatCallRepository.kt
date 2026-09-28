package com.arjun.core_alert

import com.arjun.core_alert.models.CallAlertDecision
import com.arjun.core_alert.models.VipContact

interface RepeatCallRepository {

    val busy: Boolean

    fun onRinging(contact: VipContact?): CallAlertDecision?

    fun onAnswered()

    fun onIdle()

    fun reset()
}
