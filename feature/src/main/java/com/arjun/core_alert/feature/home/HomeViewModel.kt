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
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.arjun.core_alert.AlertSettingsRepository
import com.arjun.core_alert.BootReceiver
import com.arjun.core_alert.CallMonitorService
import com.arjun.core_alert.ContactRepository
import com.arjun.core_alert.VipMessageAlertsProvider
import com.arjun.core_alert.feature.R
import com.arjun.core_alert.models.CallAlertMode
import com.arjun.core_alert.models.MessageAlertState
import com.arjun.core_alert.models.MessageApp
import com.arjun.core_alert.models.VipContact
import com.arjun.core_alert.policy.ContactImportPolicy
import com.arjun.core_alert.policy.ContactPhoneOption
import com.arjun.core_alert.policy.HomeWarningPolicy
import com.arjun.core_alert.policy.PermissionPolicy
import com.arjun.core_alert.policy.ServiceEnablePolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class HomeViewModel(
    private val context: Application,
    private val settings: AlertSettingsRepository,
    private val contactRepository: ContactRepository,
    private val messageAlerts: VipMessageAlertsProvider
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val countdownHandler = Handler(Looper.getMainLooper())
    private val countdownRunnable = object : Runnable {
        override fun run() {
            updateMuteCountdown()
            if (settings.isMuted) countdownHandler.postDelayed(this, 60_000)
        }
    }

    init {
        setState {
            it.copy(
                contacts = contactRepository.getContacts(),
                serviceEnabled = settings.isServiceEnabled
            )
        }
        refreshPermissions()
        updateMuteCountdown()
    }

    fun onResume() {
        refreshPermissions()
        setState { it.copy(serviceEnabled = settings.isServiceEnabled) }
        refreshStrings()
        reloadContacts()
        countdownHandler.removeCallbacks(countdownRunnable)
        if (settings.isMuted) countdownHandler.post(countdownRunnable)
    }

    fun refreshStrings() {
        setState()
        updateMuteCountdown()
    }

    fun onPause() {
        countdownHandler.removeCallbacks(countdownRunnable)
    }

    override fun onCleared() {
        countdownHandler.removeCallbacks(countdownRunnable)
    }

    fun reloadContacts() {
        setState { it.copy(contacts = contactRepository.getContacts()) }
    }

    fun handleAction(action: HomeAction) {
        when (action) {
            HomeAction.Refresh -> onResume()
            is HomeAction.ServiceToggled -> onServiceToggled(action.checked)
            is HomeAction.PermissionResult -> onPermissionResult(action.results)
            HomeAction.OpenDndDialog -> openDialog(HomeDialog.DndExplain)
            HomeAction.ToggleMute -> onToggleMute()
            is HomeAction.ActivateMute -> onActivateMute(action.hours)
            HomeAction.OpenAddChoice -> openDialog(HomeDialog.AddChoice)
            HomeAction.ShowManualAdd -> showManualAdd()
            is HomeAction.EditContact -> onEditContact(action.contact)
            is HomeAction.OpenContactCallMode -> openDialog(HomeDialog.ContactCallMode(action.contact))
            is HomeAction.OpenRemoveContact -> openDialog(HomeDialog.RemoveContact(action.contact))
            is HomeAction.ConfirmRemoveContact -> onDeleteContact(action.contact)
            is HomeAction.SaveContact -> onSaveContact(action.name, action.number, action.editIndex)
            is HomeAction.SaveSelectedNumbers -> onSaveSelectedNumbers(action.name, action.selected)
            is HomeAction.SetContactCallMode -> onSetContactCallMode(action.contact, action.mode)
            HomeAction.OpenCallModeSettings -> openDialog(HomeDialog.CallModeSettings)
            is HomeAction.SetCallAlertMode -> {
                settings.setCallAlertMode(action.mode)
                setState()
            }
            is HomeAction.SetRepeatWindow -> {
                settings.setRepeatCallWindowMinutes(action.minutes)
                setState()
            }
            is HomeAction.SetEscalateVolume -> {
                settings.setEscalateCallVolume(action.enabled)
                setState()
            }
            is HomeAction.ContactPicked -> onContactPicked(action.uri)
            is HomeAction.MessageAlertTap -> onMessageAlertTap(action.contact, action.app)
            is HomeAction.BeginMessagePairing -> beginMessagePairing(action.contact, action.app)
            is HomeAction.CancelMessagePairing -> cancelMessagePairing(action.contact, action.app)
            HomeAction.CloseDialog -> closeDialog()
        }
    }

    private fun setState(transform: (HomeUiState) -> HomeUiState = { it }) {
        _state.update { current -> derive(transform(current)) }
    }

    private fun derive(state: HomeUiState): HomeUiState = state.copy(
        callAlertMode = settings.callAlertMode,
        repeatWindowMinutes = settings.repeatCallWindowMinutes,
        escalateCallVolume = settings.escalateCallVolume,
        volumePercent = settings.volumePercent,
        callModeSummary = context.getString(
            if (settings.callAlertMode == CallAlertMode.SECOND) R.string.call_mode_summary_second
            else R.string.call_mode_summary_first,
            settings.repeatCallWindowMinutes
        ),
        messageStates = state.contacts
            .flatMap { contact ->
                MessageApp.entries.map { app -> messageKey(contact, app) to messageAlerts.state(contact, app) }
            }
            .toMap()
    )

    private fun openDialog(dialog: HomeDialog) {
        setState { it.copy(dialog = dialog) }
    }

    private fun closeDialog() {
        setState { it.copy(dialog = null) }
    }

    private fun updateMuteCountdown() {
        setState {
            it.copy(
                muteCountdown = if (settings.isMuted) {
                    val remaining = settings.muteUntilTimestamp - System.currentTimeMillis()
                    val hours = (remaining / 3_600_000).toInt()
                    val minutes = ((remaining % 3_600_000) / 60_000).toInt()
                    context.getString(R.string.mute_countdown, hours, minutes)
                } else null
            )
        }
    }

    private fun refreshPermissions() {
        val phoneOk = granted(Manifest.permission.READ_PHONE_STATE)
        val callLogOk = granted(Manifest.permission.READ_CALL_LOG)
        val dnd = notificationPolicyAccess()
        val criticalMissing = PermissionPolicy.criticalMissing(callLogOk, phoneOk, dnd).isNotEmpty()
        val autoRevokeActive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            !context.packageManager.isAutoRevokeWhitelisted
        } else false
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryActive = !powerManager.isIgnoringBatteryOptimizations(context.packageName)

        setState {
            it.copy(
                runtimeGranted = runtimePermissions.all { permission -> granted(permission) },
                dndGranted = dnd,
                contactsPermissionGranted = granted(Manifest.permission.READ_CONTACTS),
                warning = HomeWarningPolicy.decide(
                    settings.isServiceEnabled,
                    criticalMissing,
                    autoRevokeActive,
                    batteryActive
                )
            )
        }
    }

    private fun granted(permission: String): Boolean =
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

    private fun onPermissionResult(results: Map<String, Boolean>) {
        refreshPermissions()
        if (results.values.all { it }) toast(R.string.perm_all_granted)
        else toast(R.string.perm_some_missing, long = true)
    }

    private fun onServiceToggled(checked: Boolean) {
        if (checked) {
            if (checkAllPermissions()) {
                settings.setServiceEnabled(true)
                CallMonitorService.start(context)
                setState { it.copy(serviceEnabled = true) }
                toast(R.string.monitoring_on)
            } else {
                setState { it.copy(serviceEnabled = false) }
                toast(R.string.grant_perms_first, long = true)
            }
        } else {
            settings.setServiceEnabled(false)
            CallMonitorService.stop(context)
            setState { it.copy(serviceEnabled = false) }
            toast(R.string.monitoring_off)
        }
        refreshPermissions()
    }

    private fun onToggleMute() {
        if (settings.isMuted) {
            settings.clearMute()
            BootReceiver.cancelMuteAlarm(context)
            toast(R.string.mute_cancelled)
            updateMuteCountdown()
            countdownHandler.removeCallbacks(countdownRunnable)
        } else {
            openDialog(HomeDialog.MuteHours)
        }
    }

    private fun onActivateMute(hours: Int) {
        val muteUntil = System.currentTimeMillis() + hours * 3_600_000L
        settings.muteUntil(muteUntil)
        CallMonitorService.getInstance()?.suspendActiveAlert()
        BootReceiver.scheduleMuteAlarm(context, muteUntil)
        toast(context.getString(R.string.mute_activated, hours))
        updateMuteCountdown()
        countdownHandler.removeCallbacks(countdownRunnable)
        countdownHandler.post(countdownRunnable)
    }

    private fun showManualAdd() {
        openDialog(
            HomeDialog.ContactForm(
                title = R.string.add_choice_title,
                confirm = R.string.btn_add,
                number = "+"
            )
        )
    }

    private fun onEditContact(contact: VipContact) {
        setState {
            it.copy(
                dialog = HomeDialog.ContactForm(
                    title = R.string.edit_contact_title,
                    confirm = R.string.btn_save,
                    name = contact.name,
                    number = contact.number,
                    editIndex = it.contacts.indexOfFirst { c -> c.number == contact.number }
                )
            )
        }
    }

    private fun onSaveContact(name: String, number: String, editIndex: Int?) {
        val finalName = name.trim()
        val finalNumber = number.trim()
        val contacts = state.value.contacts
        if (finalName.isEmpty() || finalNumber.length <= 3) {
            toast(R.string.contact_invalid_input, long = true)
            return
        }
        val duplicate = contacts.filterIndexed { i, c -> i != editIndex && c.number == finalNumber }
        if (duplicate.isNotEmpty()) {
            toast(R.string.contact_numbers_already_added, long = true)
            return
        }
        if (editIndex != null && editIndex in contacts.indices) {
            val old = contacts[editIndex]
            messageAlerts.onContactChanged(old.number, finalNumber)
            setState { current ->
                current.copy(
                    contacts = current.contacts.toMutableList().also {
                        it[editIndex] = old.copy(name = finalName, number = finalNumber)
                    },
                    dialog = null
                )
            }
        } else {
            setState { current ->
                current.copy(contacts = current.contacts + VipContact(finalName, finalNumber), dialog = null)
            }
        }
        saveAndRefresh()
    }

    private fun onSaveSelectedNumbers(name: String, selected: List<ContactPhoneOption>) {
        val contacts = state.value.contacts
        if (selected.isEmpty()) {
            toast(R.string.contact_numbers_none_selected)
            return
        }
        val additions = ContactImportPolicy.createVipContacts(
            contactName = name,
            selected = selected,
            existing = contacts,
            includeLabels = true
        )
        if (additions.isEmpty()) {
            toast(R.string.contact_numbers_already_added, long = true)
            return
        }
        setState { it.copy(contacts = it.contacts + additions, dialog = null) }
        saveAndRefresh()
        toast(
            context.resources.getQuantityString(R.plurals.contact_numbers_added, additions.size, additions.size)
        )
    }

    private fun onDeleteContact(contact: VipContact) {
        messageAlerts.onContactRemoved(contact.number)
        setState { it.copy(contacts = it.contacts.filterNot { c -> c.number == contact.number }) }
        saveAndRefresh()
    }

    private fun onSetContactCallMode(contact: VipContact, mode: CallAlertMode) {
        setState { current ->
            val index = current.contacts.indexOfFirst { it.number == contact.number }
            if (index < 0) current else current.copy(
                contacts = current.contacts.toMutableList().also { it[index] = it[index].copy(callAlertMode = mode) }
            )
        }
        saveAndRefresh()
    }

    private fun saveAndRefresh() {
        contactRepository.saveContacts(state.value.contacts)
        CallMonitorService.getInstance()?.refreshNotification()
    }

    private fun onContactPicked(contactUri: Uri) {
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
            openDialog(
                HomeDialog.ContactForm(
                    title = R.string.confirm_contact_title,
                    confirm = R.string.btn_add,
                    name = name,
                    number = phoneOptions.single().number
                )
            )
        } else {
            openDialog(HomeDialog.ContactNumbers(name, phoneOptions))
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

    private fun onMessageAlertTap(contact: VipContact, app: MessageApp) {
        val appName = messageAppName(context.resources, app)
        when (messageAlerts.state(contact, app)) {
            MessageAlertState.PAIRED -> {
                messageAlerts.unpair(contact, app)
                toast(context.getString(R.string.message_unpaired, appName, contact.name))
                setState()
            }
            MessageAlertState.PAIRING -> {
                messageAlerts.cancelPairing()
                toast(context.getString(R.string.message_pair_cancelled, appName))
                setState()
            }
            MessageAlertState.UNPAIRED -> {
                if (messageAlerts.hasNotificationAccess(context)) {
                    beginMessagePairing(contact, app)
                } else {
                    openDialog(HomeDialog.MessageAccess(contact, app))
                }
            }
        }
    }

    private fun beginMessagePairing(contact: VipContact, app: MessageApp) {
        messageAlerts.beginPairing(contact, app)
        openDialog(HomeDialog.MessagePair(contact, app))
    }

    private fun cancelMessagePairing(contact: VipContact, app: MessageApp) {
        messageAlerts.cancelPairing()
        toast(context.getString(R.string.message_pair_cancelled, messageAppName(context.resources, app)))
        setState()
    }

    private fun toast(@StringRes resId: Int, long: Boolean = false) =
        toast(context.getString(resId), long = long)

    private fun toast(text: String, long: Boolean = false) =
        Toast.makeText(context, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}
