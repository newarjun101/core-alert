package com.arjun.core_alert.models

enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    MYANMAR("my");

    companion object {
        fun fromTag(tag: String?): AppLanguage = when {
            tag.isNullOrEmpty() -> SYSTEM
            else -> entries.firstOrNull { it.tag != null && (tag == it.tag || tag.startsWith("${it.tag}-")) }
                ?: SYSTEM
        }
    }
}
