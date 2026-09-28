package com.arjun.core_alert.policy

object ServiceEnablePolicy {
    fun canEnable(phone: Boolean, callLog: Boolean, dnd: Boolean, notif: Boolean): Boolean {
        return phone && callLog && dnd && notif
    }
}
