package com.arjun.core_alert.models

enum class MessageApp(val storageId: String) {
    WHATSAPP("whatsapp"),
    GOOGLE_MESSAGES("google_messages"),
    TELEGRAM("telegram"),
    VIBER("viber");

    companion object {
        fun fromStorageId(value: String?): MessageApp? = entries.firstOrNull { it.storageId == value }
    }
}
