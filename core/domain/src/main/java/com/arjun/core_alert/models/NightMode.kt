package com.arjun.core_alert.models

enum class NightMode(val storedValue: Int) {
    FOLLOW_SYSTEM(-1),
    LIGHT(1),
    DARK(2);

    companion object {
        fun fromStored(value: Int): NightMode = entries.firstOrNull { it.storedValue == value } ?: FOLLOW_SYSTEM
    }
}
