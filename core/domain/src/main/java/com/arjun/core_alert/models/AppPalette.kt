package com.arjun.core_alert.models

enum class AppPalette {
    INDACO,
    TEAL,
    ARGILLA,
    ARDESIA;

    companion object {
        fun fromStoredOrdinal(value: Int): AppPalette = values().getOrElse(value) { INDACO }
    }
}
