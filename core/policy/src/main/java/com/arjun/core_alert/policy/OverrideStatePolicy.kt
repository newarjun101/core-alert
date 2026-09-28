package com.arjun.core_alert.policy

object OverrideStatePolicy {
    fun shouldRestoreOnStart(persistedOverriding: Boolean, callStateIdle: Boolean): Boolean {
        return persistedOverriding && callStateIdle
    }
}
