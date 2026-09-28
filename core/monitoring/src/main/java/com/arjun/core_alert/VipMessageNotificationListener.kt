package com.arjun.core_alert

import android.app.Notification
import android.app.Person
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.Toast
import com.arjun.core_alert.coreui.R
import com.arjun.core_alert.models.BuildInfo
import com.arjun.core_alert.models.MessageApp
import com.arjun.core_alert.models.VipContact
import com.arjun.core_alert.policy.AlertGatePolicy
import com.arjun.core_alert.policy.VipMessageNotificationPolicy
import com.arjun.core_alert.policy.VoipCallPolicy
import com.arjun.core_alert.util.PhoneUtils
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

class VipMessageNotificationListener : NotificationListenerService(), KoinComponent {

    private val settings: AlertSettingsRepository by inject()
    private val contactRepository: ContactRepository by inject()
    private val bindings: MessageBindingRepository by inject()
    private val buildInfo: BuildInfo by inject()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handleNotification(sbn, "legacy")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        handleNotification(sbn, "ranking")
    }

    @Suppress("DEPRECATION")
    private fun handleNotification(sbn: StatusBarNotification, callback: String) {
        val app = VipMessageNotificationPolicy.appForPackage(sbn.packageName) ?: return
        val notification = sbn.notification ?: return
        if (isCallNotification(app, notification)) {
            CallMonitorService.voipCallIncoming(app.storageId, callerCandidates(notification))
            return
        }
        val rawMessages = notification.extras
            .getParcelableArray(Notification.EXTRA_MESSAGES)
            .orEmpty()
        val bundles = rawMessages.mapNotNull { it as? Bundle }
        val conversationId = notification.shortcutId?.takeIf { it.isNotBlank() }
            ?: sbn.tag?.takeIf { it.isNotBlank() }
        val isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        val isGroupConversation = notification.extras.getBoolean(
            Notification.EXTRA_IS_GROUP_CONVERSATION,
            false
        )

        val diagnosticsEnabled = buildInfo.isDebug || Log.isLoggable(TAG, Log.DEBUG)
        if (diagnosticsEnabled) {
            Timber.tag(TAG).i("Message callback=$callback app=${app.storageId} summary=$isGroupSummary " +
                    "category=${notification.category} shortcut=${notification.shortcutId != null} " +
                    "tag=${sbn.tag != null} messages=${rawMessages.size} bundles=${bundles.size} " +
                    "group=$isGroupConversation"
            )
        }

        if (!VipMessageNotificationPolicy.shouldInspect(
                app = app,
                isGroupSummary = isGroupSummary,
                isGroupConversation = isGroupConversation,
                category = notification.category,
                conversationId = conversationId,
                messageCount = rawMessages.size
            )
        ) return

        val latestMessageTime = bundles.maxOfOrNull { it.getLong(MESSAGE_TIME_KEY, 0L) }
            ?.takeIf { it > 0L }
            ?: notification.`when`.takeIf { it > 0L }
            ?: sbn.postTime
        val conversationHash = VipMessageNotificationPolicy.conversationHash(conversationId!!)
        val eventFingerprint = VipMessageNotificationPolicy.eventFingerprint(
            conversationId = conversationId,
            latestMessageTime = latestMessageTime,
            messageCount = rawMessages.size
        )

        val contacts = contactRepository.getContacts()
        val googleNumbers = if (app == MessageApp.GOOGLE_MESSAGES) {
            GoogleMessagesVipResolver.candidateNumbers(this, notification, rawMessages)
        } else {
            emptySet()
        }
        if (diagnosticsEnabled && app == MessageApp.GOOGLE_MESSAGES) {
            Timber.tag(TAG).i("Google Messages candidate phone numbers=${googleNumbers.size}")
        }

        val pending = bindings.pending
        if (pending?.app == app) {
            val contact = contacts.firstOrNull { PhoneUtils.normalize(it.number) == pending.number }
            if (contact == null) {
                bindings.cancelPairing()
                return
            }
            if (googleNumbers.isNotEmpty() && googleNumbers.none { PhoneUtils.matches(it, contact.number) }) {
                Timber.tag(TAG).i("Ignoring Google Messages conversation that does not match pending VIP")
                return
            }
            bindings.bind(contact.number, app, conversationHash, eventFingerprint)
            Toast.makeText(
                this,
                getString(R.string.message_pair_success, appName(app), contact.name),
                Toast.LENGTH_LONG
            ).show()
            Timber.tag(TAG).i("Message conversation paired with VIP; app=${app.storageId}")
            return
        }

        val pairedContact = bindings.contactForConversation(app, conversationHash, contacts)
        if (pairedContact != null) {
            if (!bindings.isNewEvent(app, conversationHash, eventFingerprint)) return
            playAlertIfAllowed(pairedContact, app)
            return
        }

        if (app == MessageApp.GOOGLE_MESSAGES) {
            val autoMatched = GoogleMessagesVipResolver.uniqueVip(googleNumbers, contacts) ?: return
            bindings.bind(autoMatched.number, app, conversationHash, eventFingerprint)
            Toast.makeText(
                this,
                getString(R.string.message_auto_paired, autoMatched.name),
                Toast.LENGTH_LONG
            ).show()
            Timber.tag(TAG).i("Google Messages conversation auto-paired with VIP")
            playAlertIfAllowed(autoMatched, app)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        val app = VipMessageNotificationPolicy.appForPackage(sbn.packageName) ?: return
        if (!isCallNotification(app, notification)) return
        Timber.tag(TAG).i("VoIP call notification removed; handing audio back.")
        CallMonitorService.voipCallEnded()
    }

    private fun isCallNotification(app: MessageApp, notification: Notification): Boolean =
        VoipCallPolicy.isCallNotification(
            app = app,
            category = notification.category,
            hasFullScreenIntent = notification.fullScreenIntent != null
        )

    @Suppress("DEPRECATION")
    private fun callerCandidates(notification: Notification): List<String> {
        val numbers = linkedSetOf<String>()
        fun collect(value: Any?) {
            when (value) {
                is Person -> collect(value.uri?.toString())
                is List<*> -> value.forEach(::collect)
                is Array<*> -> value.forEach(::collect)
                is CharSequence -> {
                    val raw = value.toString()
                    val digits = raw.substringAfter("tel:", raw).filter { it.isDigit() || it == '+' }
                    if (digits.length >= 4) numbers.add(digits)
                }
            }
        }
        collect(notification.extras[Notification.EXTRA_PEOPLE_LIST])
        collect(notification.extras[Notification.EXTRA_PEOPLE])
        collect(notification.extras[Notification.EXTRA_CALL_PERSON])
        return numbers.toList()
    }

    private fun playAlertIfAllowed(contact: VipContact, app: MessageApp) {
        if (!AlertGatePolicy.allowsMessage(
                monitoringEnabled = settings.isServiceEnabled,
                paused = settings.isMuted,
                quietHours = settings.isInQuietPeriod(),
                messageSoundEnabled = settings.messageSoundEnabled
            )
        ) {
            Timber.tag(TAG).i("VIP message alert skipped by monitoring state; app=${app.storageId}")
            return
        }
        Timber.tag(TAG).i("New message from paired VIP; app=${app.storageId}")
        CallMonitorService.playMessageAlert(this, contact.number)
    }

    private fun appName(app: MessageApp): String = getString(
        when (app) {
            MessageApp.WHATSAPP -> R.string.message_app_whatsapp
            MessageApp.GOOGLE_MESSAGES -> R.string.message_app_google_messages
            MessageApp.TELEGRAM -> R.string.message_app_telegram
            MessageApp.VIBER -> R.string.message_app_viber
        }
    )

    companion object {
        private const val TAG = "CoreAlertMessages"
        private const val MESSAGE_TIME_KEY = "time"
    }
}
