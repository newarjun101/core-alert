package com.arjun.core_alert

import com.arjun.core_alert.models.CallAlertMode
import com.arjun.core_alert.models.VipContact
import org.json.JSONArray
import org.json.JSONObject

internal class ContactRepositoryImpl(private val source: PrefsDataSource) : ContactRepository {

    override fun getContacts(): List<VipContact> {
        val json = source.prefs.getString(KEY_CONTACTS, null) ?: return EMPTY_CONTACTS
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                VipContact(
                    name = obj.getString("name"),
                    number = obj.getString("number"),
                    ringtoneEnabled = obj.optBoolean("ringtoneEnabled", true),
                    callAlertMode = CallAlertMode.parse(obj.optString("callAlertMode"), CallAlertMode.INHERIT)
                )
            }
        } catch (e: Exception) {
            EMPTY_CONTACTS
        }
    }

    override fun saveContacts(contacts: List<VipContact>) {
        val arr = JSONArray()
        contacts.forEach { contact ->
            arr.put(JSONObject().apply {
                put("name", contact.name)
                put("number", contact.number)
                put("ringtoneEnabled", contact.ringtoneEnabled)
                put("callAlertMode", contact.callAlertMode.name)
            })
        }
        source.put(KEY_CONTACTS, arr.toString(), callScoped = true)
    }

    companion object {
        private const val KEY_CONTACTS = "vip_contacts"
        private val EMPTY_CONTACTS = emptyList<VipContact>()
    }
}
