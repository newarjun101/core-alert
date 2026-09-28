package com.arjun.core_alert

import com.arjun.core_alert.models.VipContact
import com.arjun.core_alert.util.PhoneUtils

interface ContactRepository {

    fun getContacts(): List<VipContact>

    fun saveContacts(contacts: List<VipContact>)

    fun getVipNumbers(): Set<String> = getContacts().map { PhoneUtils.normalize(it.number) }.toSet()

    fun findVipContact(incoming: String): VipContact? {
        if (PhoneUtils.normalize(incoming).isBlank()) return null
        return getContacts().find { contact -> PhoneUtils.matches(incoming, contact.number) }
    }
}
