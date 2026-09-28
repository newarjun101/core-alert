package com.arjun.core_alert.models

enum class CallAlertMode { INHERIT, FIRST, SECOND;
    companion object {
        fun parse(value: String?, fallback: CallAlertMode = FIRST): CallAlertMode =
            entries.firstOrNull { it.name == value } ?: fallback
    }
}
