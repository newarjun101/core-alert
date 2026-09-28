package com.arjun.core_alert.policy

import com.arjun.core_alert.util.PhoneUtils
import com.arjun.core_alert.models.VipContact

data class ContactPhoneOption(val number: String, val label: String)

object ContactImportPolicy {
    fun uniqueOptions(options: List<ContactPhoneOption>): List<ContactPhoneOption> = options
        .map { it.copy(number = it.number.trim(), label = it.label.trim()) }
        .filter { it.number.count(Char::isDigit) >= 4 }
        .distinctBy { PhoneUtils.normalize(it.number) }

    fun createVipContacts(
        contactName: String,
        selected: List<ContactPhoneOption>,
        existing: List<VipContact>,
        includeLabels: Boolean
    ): List<VipContact> {
        val unique = uniqueOptions(selected)
        return unique
            .filterNot { option -> existing.any { PhoneUtils.matches(it.number, option.number) } }
            .map { option ->
                VipContact(
                    name = displayName(contactName, option, includeLabels),
                    number = option.number
                )
            }
    }

    fun displayName(
        contactName: String,
        option: ContactPhoneOption,
        includeLabel: Boolean
    ): String {
        val base = contactName.trim()
        if (!includeLabel) return base
        val suffix = option.label.ifBlank { option.number }
        return "$base ($suffix)"
    }
}
