package com.arjun.core_alert.models

data class QuietRule(
    val days: Set<Int>,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int
)
