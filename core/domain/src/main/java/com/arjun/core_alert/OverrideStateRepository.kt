package com.arjun.core_alert

interface OverrideStateRepository {

    val isActive: Boolean

    val savedAlarmVolume: Int

    val savedDndFilter: Int

    fun beginOverride(savedAlarmVolume: Int, savedDndFilter: Int)

    fun endOverride()
}
