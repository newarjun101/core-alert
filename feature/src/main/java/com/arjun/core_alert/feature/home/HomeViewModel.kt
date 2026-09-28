package com.arjun.core_alert.feature.home

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.ContactsContract
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.arjun.core_alert.AlertSettingsRepository
import com.arjun.core_alert.BootReceiver
import com.arjun.core_alert.models.CallAlertMode
import com.arjun.core_alert.CallMonitorService
import com.arjun.core_alert.ContactRepository
import com.arjun.core_alert.models.MessageAlertState
import com.arjun.core_alert.models.MessageApp
import com.arjun.core_alert.policy.PermissionPolicy
import com.arjun.core_alert.feature.R
import com.arjun.core_alert.policy.ServiceEnablePolicy
import com.arjun.core_alert.models.VipContact
import com.arjun.core_alert.VipMessageAlertsProvider
import com.arjun.core_alert.policy.ContactImportPolicy
import com.arjun.core_alert.policy.ContactPhoneOption
import com.arjun.core_alert.policy.HomeWarning
import com.arjun.core_alert.policy.HomeWarningPolicy

class HomeViewModel(
    private val context: Application,
    val settings: AlertSettingsRepository,
    private val contactRepository: ContactRepository,
    val messageAlerts: VipMessageAlertsProvider
) : ViewModel() {

    var contacts by mutableStateOf(contactRepository.getContacts())
        private set
    var serviceEnabled by mutableStateOf(settings.isServiceEnabled)
    var runtimeGranted by mutableStateOf(false)
    var dndGranted by mutableStateOf(false)
    var canEnable by mutableStateOf(false)
    var warning by mutableStateOf(HomeWarning.NONE)
    var muteCountdown by mutableStateOf<String?>(null)
    var callModeSummary by mutableStateOf(buildCallModeSummary())
    var dialog by mutableStateOf<HomeDialog?>(null)

    val runtimePermissions: Array<String> = buildList {
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val countdownHandler = Handler(Looper.getMainLooper())
    private val countdownRunnable = object : Runnable {
        override fun run() {
            updateMuteCountdown()
            if (settings.isMuted) countdownHandler.postDelayed(this, 60_000)
        }
    }

    fun onResume() {
        refreshPermissions()
        serviceEnabled = settings.isServiceEnabled
        refreshStrings()
        reloadContacts()
        countdownHandler.removeCallbacks(countdownRunnable)
        if (settings.isMuted) countdownHandler.post(countdownRunnable)
    }

    fun refreshStrings() {
        callModeSummary = buildCallModeSummary()
        updateMuteCountdown()
    }

    fun onPause() {
        countdownHandler.removeCallbacks(countdownRunnable)
    }

    override fun onCleared() {
        countdownHandler.removeCallbacks(countdownRunnable)
    }

    private fun updateMuteCountdown() {
        muteCountdown = if (settings.isMuted) {
            val remaining = settings.muteUntilTimestamp - System.currentTimeMillis()
            val hours = (remaining / 3_600_000).toInt()
            val minutes = ((remaining % 3_600_000) / 60_000).toInt()
            context.getString(R.string.mute_countdown, hours, minutes)
        } else null
    }

    fun refreshPermissions() {
        val phoneOk = granted(Manifest.permission.READ_PHONE_STATE)
        val callLogOk = granted(Manifest.permission.READ_CALL_LOG)
        val notifOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted(Manifest.permission.POST_NOTIFICATIONS)
        } else true
        val dnd = notificationPolicyAccess()
        runtimeGranted = runtimePermissions.all { granted(it) }
        dndGranted = dnd
        canEnable = ServiceEnablePolicy.canEnable(phoneOk, callLogOk, dnd, notifOk)

        val criticalMissing = PermissionPolicy.criticalMissing(callLogOk, phoneOk, dnd).isNotEmpty()
        val autoRevokeActive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            !context.packageManager.isAutoRevokeWhitelisted
        } else false
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryActive = !powerManager.isIgnoringBatteryOptimizations(context.packageName)
        warning = HomeWarningPolicy.decide(settings.isServiceEnabled, criticalMissing, autoRevokeActive, batteryActive)
    }

    fun onPermissionResult(results: Map<String, Boolean>) {
        refreshPermissions()
        if (results.values.all { it }) toast(R.string.perm_all_granted)
        else toast(R.string.perm_some_missing, long = true)
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun notificationPolicyAccess(): Boolean =
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .isNotificationPolicyAccessGranted

    private fun checkAllPermissions(): Boolean =
        ServiceEnablePolicy.canEnable(
            granted(Manifest.permission.READ_PHONE_STATE),
            granted(Manifest.permission.READ_CALL_LOG),
            notificationPolicyAccess(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) granted(Manifest.permission.POST_NOTIFICATIONS) else true
        )

    fun openDndDialog() { dialog = HomeDialog.DndExplain }

    fun onServiceToggled(checked: Boolean) {
        if (checked) {
            if (checkAllPermissions()) {
                settings.setServiceEnabled(true)
                CallMonitorService.start(context)
                serviceEnabled = true
                toast(R.string.monitoring_on)
            } else {
                serviceEnabled = false
                toast(R.string.grant_perms_first, long = true)
            }
        } else {
            settings.setServiceEnabled(false)
            CallMonitorService.stop(context)
            serviceEnabled = false
            toast(R.string.monitoring_off)
        }
        refreshPermissions()
    }

    fun toggleMute() {
        if (settings.isMuted) {
            settings.clearMute()
            BootReceiver.cancelMuteAlarm(context)
            toast(R.string.mute_cancelled)
            updateMuteCountdown()
            countdownHandler.removeCallbacks(countdownRunnable)
        } else {
            dialog = HomeDialog.MuteHours
        }
    }

    fun activateMute(hours: Int) {
        val muteUntil = System.currentTimeMillis() + hours * 3_600_000L
        settings.muteUntil(muteUntil)
        CallMonitorService.getInstance()?.suspendActiveAlert()
        BootReceiver.scheduleMuteAlarm(context, muteUntil)
        toast(context.getString(R.string.mute_activated, hours))
        updateMuteCountdown()
        countdownHandler.removeCallbacks(countdownRunnable)
        countdownHandler.post(countdownRunnable)
    }

    fun reloadContacts() {
        contacts = contactRepository.getContacts()
    }

    private fun saveAndRefresh() {
        contactRepository.saveContacts(contacts)
        CallMonitorService.getInstance()?.refreshNotification()
    }

    fun openAddChoice() { dialog = HomeDialog.AddChoice }

    fun needsContactsPermission(): Boolean = !granted(Manifest.permission.READ_CONTACTS)

    fun showManualAdd() {
        dialog = HomeDialog.ContactForm(
            title = R.string.add_choice_title,
            confirm = R.string.btn_add,
            number = "+"
        )
    }

    fun saveContact(name: String, number: String, editIndex: Int?): Boolean {
        val finalName = name.trim()
        val finalNumber = number.trim()
        if (finalName.isEmpty() || finalNumber.length <= 3) {
            toast(R.string.contact_invalid_input, long = true)
            return false
        }
        val duplicate = contacts.filterIndexed { i, c -> i != editIndex && c.number == finalNumber }
        if (duplicate.isNotEmpty()) {
            toast(R.string.contact_numbers_already_added, long = true)
            return false
        }
        if (editIndex != null && editIndex in contacts.indices) {
            val old = contacts[editIndex]
            messageAlerts.onContactChanged(old.number, finalNumber)
            contacts = contacts.toMutableList().also { it[editIndex] = old.copy(name = finalName, number = finalNumber) }
        } else {
            contacts = contacts + VipContact(finalName, finalNumber)
        }
        saveAndRefresh()
        return true
    }

    fun saveSelectedNumbers(name: String, selected: List<ContactPhoneOption>): Boolean {
        if (selected.isEmpty()) {
            toast(R.string.contact_numbers_none_selected)
            return false
        }
        val additions = ContactImportPolicy.createVipContacts(
            contactName = name,
            selected = selected,
            existing = contacts,
            includeLabels = true
        )
        if (additions.isEmpty()) {
            toast(R.string.contact_numbers_already_added, long = true)
            return false
        }
        contacts = contacts + additions
        saveAndRefresh()
        toast(
            context.resources.getQuantityString(R.plurals.contact_numbers_added, additions.size, additions.size)
        )
        return true
    }

    fun deleteContact(contact: VipContact) {
        messageAlerts.onContactRemoved(contact.number)
        contacts = contacts.filterNot { it.number == contact.number }
        saveAndRefresh()
    }

    fun setContactCallMode(contact: VipContact, mode: CallAlertMode) {
        val index = contacts.indexOfFirst { it.number == contact.number }
        if (index >= 0) {
            contacts = contacts.toMutableList().also { it[index] = it[index].copy(callAlertMode = mode) }
            saveAndRefresh()
        }
    }

    fun openCallModeSettings() { dialog = HomeDialog.CallModeSettings }

    fun repeatWindowMinutes(): Int = settings.repeatCallWindowMinutes

    fun onCallModeSettingsChanged() {
        callModeSummary = buildCallModeSummary()
    }

    private fun buildCallModeSummary(): String = context.getString(
        if (settings.callAlertMode == CallAlertMode.SECOND) R.string.call_mode_summary_second
        else R.string.call_mode_summary_first,
        settings.repeatCallWindowMinutes
    )

    fun onContactPicked(contactUri: Uri) {
        var name = ""
        var contactId = ""
        val nameCursor: Cursor? = context.contentResolver.query(
            contactUri,
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts._ID),
            null, null, null
        )
        nameCursor?.use {
            if (it.moveToFirst()) {
                name = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)) ?: ""
                contactId = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts._ID)) ?: ""
            }
        }

        val phoneOptions = readContactPhones(contactId)
        if (phoneOptions.isEmpty()) {
            toast(R.string.no_phone_found, long = true)
            return
        }
        if (phoneOptions.size == 1) {
            dialog = HomeDialog.ContactForm(
                title = R.string.confirm_contact_title,
                confirm = R.string.btn_add,
                name = name,
                number = phoneOptions.single().number
            )
        } else {
            dialog = HomeDialog.ContactNumbers(name, phoneOptions)
        }
    }

    private fun readContactPhones(contactId: String): List<ContactPhoneOption> {
        if (contactId.isBlank()) return emptyList()
        val raw = mutableListOf<ContactPhoneOption>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL
            ),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            ContactsContract.CommonDataKinds.Phone.IS_PRIMARY + " DESC"
        )?.use { cursor ->
            val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
            val labelIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL)
            while (cursor.moveToNext()) {
                val number = cursor.getString(numberIndex).orEmpty()
                val type = cursor.getInt(typeIndex)
                val customLabel = cursor.getString(labelIndex)
                val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                    context.resources, type, customLabel
                ).toString()
                raw += ContactPhoneOption(number, label)
            }
        }
        return ContactImportPolicy.uniqueOptions(raw)
    }

    fun phoneOptionLabel(option: ContactPhoneOption): String =
        if (option.label.isBlank()) option.number
        else context.getString(R.string.contact_number_option, option.label, option.number)

    fun messageAppName(app: MessageApp): String = context.getString(
        when (app) {
            MessageApp.WHATSAPP -> R.string.message_app_whatsapp
            MessageApp.GOOGLE_MESSAGES -> R.string.message_app_google_messages
            MessageApp.TELEGRAM -> R.string.message_app_telegram
            MessageApp.VIBER -> R.string.message_app_viber
        }
    )

    fun messageMenuTitle(contact: VipContact, app: MessageApp): String {
        val appName = messageAppName(app)
        return when (messageAlerts.state(contact, app)) {
            MessageAlertState.UNPAIRED -> context.getString(R.string.message_pair_app, appName)
            MessageAlertState.PAIRING -> context.getString(R.string.message_pair_cancel_app, appName)
            MessageAlertState.PAIRED -> context.getString(R.string.message_unpair_app, appName)
        }
    }

    fun onMessageAlertTap(contact: VipContact, app: MessageApp) {
        val appName = messageAppName(app)
        when (messageAlerts.state(contact, app)) {
            MessageAlertState.PAIRED -> {
                messageAlerts.unpair(contact, app)
                toast(context.getString(R.string.message_unpaired, appName, contact.name))
            }
            MessageAlertState.PAIRING -> {
                messageAlerts.cancelPairing()
                toast(context.getString(R.string.message_pair_cancelled, appName))
            }
            MessageAlertState.UNPAIRED -> {
                if (messageAlerts.hasNotificationAccess(context)) {
                    beginMessagePairing(contact, app)
                } else {
                    dialog = HomeDialog.MessageAccess(contact, app)
                }
            }
        }
    }

    fun beginMessagePairing(contact: VipContact, app: MessageApp) {
        messageAlerts.beginPairing(contact, app)
        dialog = HomeDialog.MessagePair(contact, app)
    }

    fun cancelMessagePairing(contact: VipContact, app: MessageApp) {
        messageAlerts.cancelPairing()
        toast(context.getString(R.string.message_pair_cancelled, messageAppName(app)))
    }

    private fun toast(@StringRes resId: Int, long: Boolean = false) =
        toast(context.getString(resId), long = long)

    private fun toast(text: String, long: Boolean = false) =
        Toast.makeText(context, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()

    fun closeDialog() { dialog = null }
}
