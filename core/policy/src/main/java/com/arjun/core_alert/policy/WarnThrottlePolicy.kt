package com.arjun.core_alert.policy

object WarnThrottlePolicy {
    const val INTERVAL_MS = 24L * 60 * 60 * 1000
    fun shouldWarn(lastWarnMs: Long, nowMs: Long): Boolean = nowMs - lastWarnMs >= INTERVAL_MS
}
