package com.arjun.core_alert.models

data class VipContact(
    val name: String,
    val number: String,
    val ringtoneEnabled: Boolean = true,
    val callAlertMode: CallAlertMode = CallAlertMode.INHERIT
)
