package com.arjun.core_alert.policy

object MutePolicy {
    fun isMuted(untilMs: Long, nowMs: Long): Boolean {
        if (untilMs == 0L) return false
        return nowMs < untilMs
    }
}
