package com.arjun.core_alert

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telephony.TelephonyManager
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.arjun.core_alert.coreui.R
import com.arjun.core_alert.models.CallAlertDecision
import com.arjun.core_alert.models.VipContact
import com.arjun.core_alert.policy.AlertGatePolicy
import com.arjun.core_alert.policy.AlertVolumePolicy
import com.arjun.core_alert.policy.AudioOverridePolicy
import com.arjun.core_alert.policy.OverrideStatePolicy
import com.arjun.core_alert.policy.PermissionPolicy
import com.arjun.core_alert.policy.VoipCallPolicy
import com.arjun.core_alert.policy.WarnThrottlePolicy
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import timber.log.Timber

class CallMonitorService : Service(), KoinComponent {

    companion object {
        private const val TAG = "CoreAlert"
        private const val CHANNEL_ID = "corealert_channel"
        private const val NOTIFICATION_ID = 1
        private const val OVERRIDE_CHANNEL_ID = "corealert_override"
        private const val OVERRIDE_NOTIFICATION_ID = 2
        private const val PERM_WARNING_NOTIFICATION_ID = 5
        private const val ACTION_MESSAGE_ALERT = "com.arjun.core_alert.MESSAGE_VIP_ALERT"
        private const val EXTRA_VIP_NUMBER = "vip_number"
        private const val MESSAGE_ALERT_TIMEOUT_MS = 15_000L
        private const val ALERT_GATE_CHECK_MS = 1_000L
        private const val VOIP_RING_TIMEOUT_MS = 60_000L
        private const val MAIN_ACTIVITY_CLASS = "com.arjun.core_alert.MainActivity"

        fun start(context: Context) {
            val intent = Intent(context, CallMonitorService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallMonitorService::class.java))
        }

        fun playMessageAlert(context: Context, number: String) {
            instance?.let {
                it.overrideMessageAlert(number)
                return
            }
            val intent = Intent(context, CallMonitorService::class.java).apply {
                action = ACTION_MESSAGE_ALERT
                putExtra(EXTRA_VIP_NUMBER, number)
            }
            context.startForegroundService(intent)
        }

        fun voipCallIncoming(appLabel: String, callerNumbers: List<String>) {
            val service = instance
            if (service == null) {
                Timber.tag(TAG).d("VoIP call ($appLabel) while the monitoring service is down; ignored.")
                return
            }
            service.onVoipCall(appLabel, callerNumbers)
        }

        fun voipCallEnded() {
            instance?.onVoipCallEnded()
        }

        private var instance: CallMonitorService? = null
        fun getInstance(): CallMonitorService? = instance
    }

    private val settings: AlertSettingsRepository by inject()
    private val contactRepository: ContactRepository by inject()
    private val overrideState: OverrideStateRepository by inject()
    private lateinit var audioManager: AudioManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var repeatCalls: RepeatCallRepository
    private val alertChangeHandler: () -> Unit = {
        repeatCalls.reset()
        if (!AlertGatePolicy.allowsCall(settings.isServiceEnabled, settings.isMuted, settings.isInQuietPeriod())) {
            suspendActiveAlert()
        }
    }

    private var isOverriding = false
    private var overrideKind: OverrideKind? = null
    private var savedAlarmVolume = 0
    private var savedDndFilter = NotificationManager.INTERRUPTION_FILTER_ALL
    private val overrideHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    private var currentRingingNumber: String? = null
    private var voipCallActive = false

    private val voipTimeout = Runnable {
        if (!voipCallActive) return@Runnable
        if (isOverriding && overrideKind == OverrideKind.CALL) {
            Timber.tag(TAG).i("VoIP ring timed out; restoring audio.")
            restoreAudio()
        }
    }

    private val phoneReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
            // Android sends a second, numberless broadcast. Its delivery order is unspecified.
            if (!intent.hasExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)) return

            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            voipCallActive = false

            val numberHash = number?.let {
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(it.toByteArray())
                    .joinToString("") { "%02x".format(it) }
                    .take(8)
            }
            Timber.tag(TAG).d("Phone state: $state, hash: $numberHash")

            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    this@CallMonitorService.warnIfCriticalPermsMissing()
                    if (isOverriding && overrideKind == OverrideKind.MESSAGE) restoreAudio()
                    val vipContact = number?.let(::findVipContact)
                    val decision = repeatCalls.onRinging(vipContact)
                    if (decision?.shouldRing == true && vipContact != null) {
                        overrideAudio(vipContact.number, decision)
                    }
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    repeatCalls.onIdle()
                    if (isOverriding && overrideKind == OverrideKind.CALL) {
                        Timber.tag(TAG).i("Call ended. Restoring audio.")
                        restoreAudio()
                    }
                }
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    repeatCalls.onAnswered()
                    if (isOverriding && overrideKind == OverrideKind.MESSAGE) restoreAudio()
                    // Call answered — stop ringtone/vibration, restore audio on IDLE
                    if (isOverriding && overrideKind == OverrideKind.CALL) {
                        Timber.tag(TAG).i("Call answered. Stopping ringtone.")
                        stopRingtoneAndVibration()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        repeatCalls = get { parametersOf(!isCallStateIdle()) }
        settings.addAlertChangeListener(alertChangeHandler)

        restoreStaleOverrideState()

        createNotificationChannels()
        startForeground(NOTIFICATION_ID, buildPersistentNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        registerReceiver(phoneReceiver, filter)

        // A call can end between the initial state snapshot and receiver registration.
        if (repeatCalls.busy && isCallStateIdle()) repeatCalls.onIdle()

        if (isOverriding && isCallStateIdle()) {
            Timber.tag(TAG).i("Call went idle before receiver registration; restoring audio.")
            restoreAudio()
        }

        Timber.tag(TAG).i("Service started. Monitoring ${contactRepository.getVipNumbers().size} VIP numbers.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_MESSAGE_ALERT) {
            intent.getStringExtra(EXTRA_VIP_NUMBER)?.let(::overrideMessageAlert)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        settings.removeAlertChangeListener(alertChangeHandler)
        overrideHandler.removeCallbacksAndMessages(null)
        stopRingtoneAndVibration()
        if (isOverriding) restoreAudio()
        unregisterReceiver(phoneReceiver)
        Timber.tag(TAG).i("Service stopped.")
        instance = null
        currentRingingNumber = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun restoreStaleOverrideState() {
        val persistedOverriding = overrideState.isActive
        if (!persistedOverriding) return

        savedAlarmVolume = overrideState.savedAlarmVolume
        savedDndFilter = overrideState.savedDndFilter
        isOverriding = true
        overrideKind = OverrideKind.CALL

        if (OverrideStatePolicy.shouldRestoreOnStart(persistedOverriding, isCallStateIdle())) {
            Timber.tag(TAG).i("Self-healing stale override state after process restart.")
            restoreAudio()
        } else {
            Timber.tag(TAG).i("Call still active after process restart; deferring restore of stale override state.")
        }
    }

    @Suppress("DEPRECATION")
    private fun isCallStateIdle(): Boolean {
        return try {
            val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            telephonyManager.callState == TelephonyManager.CALL_STATE_IDLE
        } catch (e: SecurityException) {
            Timber.tag(TAG).w("Cannot read call state: ${e.message}")
            false
        }
    }

    private fun warnIfCriticalPermsMissing() {
        if (!settings.isServiceEnabled) return
        val callLogOk = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        val phoneStateOk = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val dndOk = notificationManager.isNotificationPolicyAccessGranted
        val missing = PermissionPolicy.criticalMissing(callLogOk, phoneStateOk, dndOk)
        if (missing.isEmpty()) return
        val now = System.currentTimeMillis()
        if (!WarnThrottlePolicy.shouldWarn(settings.lastPermissionWarningMs, now)) return
        settings.setLastPermissionWarningMs(now)
        val openIntent = PendingIntent.getActivity(
            this, PERM_WARNING_NOTIFICATION_ID,
            Intent().setClassName(packageName, MAIN_ACTIVITY_CLASS)
                .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, OVERRIDE_CHANNEL_ID)
            .setContentTitle(getString(R.string.perm_revoked_notif_title))
            .setContentText(getString(R.string.perm_revoked_notif_text))
            .setSmallIcon(R.drawable.ic_notification_sos)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()
        try {
            notificationManager.notify(PERM_WARNING_NOTIFICATION_ID, notif)
        } catch (e: SecurityException) {
            Timber.tag(TAG).e("Cannot post permission warning: ${e.message}")
        }
    }

    private fun findVipContact(incoming: String): VipContact? {
        return contactRepository.findVipContact(incoming)
    }

    private fun onVoipCall(appLabel: String, callerNumbers: List<String>) {
        if (isOverriding && overrideKind == OverrideKind.MESSAGE) restoreAudio()
        if (isOverriding) return
        val vip = callerNumbers.firstNotNullOfOrNull(::findVipContact)
        if (!VoipCallPolicy.shouldRing(callerNumbers, vip != null)) {
            Timber.tag(TAG).i("VoIP call skipped: caller is not a VIP contact (app=$appLabel).")
            return
        }
        if (!AlertGatePolicy.allowsCall(settings.isServiceEnabled, settings.isMuted, settings.isInQuietPeriod())) return
        warnIfCriticalPermsMissing()
        val decision = repeatCalls.onRinging(vip)
            ?: CallAlertDecision(shouldRing = true, volumePercent = settings.volumePercent, exactVolume = false)
        if (!decision.shouldRing) return
        Timber.tag(TAG).i("VoIP call incoming (app=$appLabel, vip=${vip != null}).")
        voipCallActive = true
        overrideAudio(vip?.number ?: callerNumbers.firstOrNull().orEmpty(), decision)
        if (isOverriding) overrideHandler.postDelayed(voipTimeout, VOIP_RING_TIMEOUT_MS)
    }

    private fun onVoipCallEnded() {
        if (!voipCallActive) return
        voipCallActive = false
        if (isCallStateIdle()) repeatCalls.onIdle()
        if (isOverriding && overrideKind == OverrideKind.CALL) {
            Timber.tag(TAG).i("VoIP call ended. Restoring audio.")
            restoreAudio()
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    @Suppress("DEPRECATION")
    private fun overrideAudio(number: String, decision: CallAlertDecision) {
        if (!beginAudioOverride(number, OverrideKind.CALL, decision.volumePercent, decision.exactVolume)) return

        startOverrideSound()

        // Start vibration
        startVibration()
        notificationManager.notify(OVERRIDE_NOTIFICATION_ID, buildOverrideNotification())
    }

    private fun overrideMessageAlert(number: String) {
        if (repeatCalls.busy || !isCallStateIdle()) return
        if (!AlertGatePolicy.allowsMessage(
                monitoringEnabled = settings.isServiceEnabled,
                paused = settings.isMuted,
                quietHours = settings.isInQuietPeriod(),
                messageSoundEnabled = settings.messageSoundEnabled
            )
        ) return
        if (!beginAudioOverride(number, OverrideKind.MESSAGE, settings.messageVolumePercent)) return

        val customUri = if (settings.messageSoundType == AlertSettingsRepository.MESSAGE_SOUND_CONTACT) {
            ContactRingtoneHelper.getRingtoneUri(this, number)
        } else {
            null
        }
        val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val candidates = listOfNotNull(
            customUri?.let { it to "VIP contact sound" },
            defaultUri?.let { it to "default notification" },
            alarmUri?.let { it to "alarm" }
        )
        val finish = {
            if (isOverriding && overrideKind == OverrideKind.MESSAGE) restoreAudio()
        }
        playCandidate(candidates, 0, looping = false, onCompletion = finish, onAllFailed = finish)
        overrideHandler.postDelayed(finish, MESSAGE_ALERT_TIMEOUT_MS)
    }

    @Suppress("DEPRECATION")
    private fun beginAudioOverride(number: String, kind: OverrideKind, volumePercent: Int, exactCallVolume: Boolean = false): Boolean {
        if (isOverriding) return false
        if (!AudioOverridePolicy.shouldOverride(
                audioManager.ringerMode,
                audioManager.getStreamVolume(AudioManager.STREAM_RING),
                notificationManager.currentInterruptionFilter
            )) {
            Timber.tag(TAG).i("System already audible; ${kind.name.lowercase()} override skipped.")
            return false
        }

        currentRingingNumber = number
        savedAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        savedDndFilter = notificationManager.currentInterruptionFilter
        isOverriding = true
        overrideKind = kind
        overrideState.beginOverride(savedAlarmVolume, savedDndFilter)

        return try {
            Timber.tag(TAG).d("Saved state: alarmVol=$savedAlarmVolume, dnd=$savedDndFilter")
            if (notificationManager.isNotificationPolicyAccessGranted) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                Timber.tag(TAG).d("DND overridden to FILTER_ALL")
            } else {
                Timber.tag(TAG).w("DND permission NOT granted!")
                notificationManager.notify(
                    OVERRIDE_NOTIFICATION_ID + 1,
                    NotificationCompat.Builder(this, OVERRIDE_CHANNEL_ID)
                        .setContentTitle(getString(R.string.notif_override_title))
                        .setContentText(getString(R.string.dnd_revoked_warning))
                        .setSmallIcon(R.drawable.ic_notification_sos)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .build()
                )
            }

            val alarmMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val alarmCurrent = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val alarmFinal = AlertVolumePolicy.targetStreamVolume(
                maxVolume = alarmMax,
                currentVolume = alarmCurrent,
                percent = volumePercent,
                preserveHigherCurrentVolume = kind == OverrideKind.CALL && !exactCallVolume
            )
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, alarmFinal, 0)
            Timber.tag(TAG).d("Override playback: alarm volume=$alarmFinal (app=$volumePercent%, system=$alarmCurrent)")
            scheduleAlertGateCheck()
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e("Error during audio override: ${e.message}", e)
            restoreAudio()
            false
        }
    }

    fun suspendActiveAlert() {
        if (isOverriding) restoreAudio()
    }

    fun suspendMessageAlert() {
        if (isOverriding && overrideKind == OverrideKind.MESSAGE) restoreAudio()
    }

    private fun scheduleAlertGateCheck() {
        overrideHandler.postDelayed(object : Runnable {
            override fun run() {
                if (!isOverriding) return
                val allowed = when (overrideKind) {
                    OverrideKind.MESSAGE -> AlertGatePolicy.allowsMessage(
                        monitoringEnabled = settings.isServiceEnabled,
                        paused = settings.isMuted,
                        quietHours = settings.isInQuietPeriod(),
                        messageSoundEnabled = settings.messageSoundEnabled
                    )
                    OverrideKind.CALL -> AlertGatePolicy.allowsCall(
                        monitoringEnabled = settings.isServiceEnabled,
                        paused = settings.isMuted,
                        quietHours = settings.isInQuietPeriod()
                    )
                    null -> false
                }
                if (!allowed) {
                    Timber.tag(TAG).i("Active alert stopped by monitoring state.")
                    restoreAudio()
                    return
                }
                overrideHandler.postDelayed(this, ALERT_GATE_CHECK_MS)
            }
        }, ALERT_GATE_CHECK_MS)
    }

    private fun startOverrideSound() {
        val customUri = currentRingingNumber?.takeIf { it.isNotBlank() }?.let {
            ContactRingtoneHelper.getRingtoneUri(this, it)
        }

        val useNotificationSound = settings.overrideSoundType == AlertSettingsRepository.SOUND_TYPE_NOTIFICATION
        val defaultUri = RingtoneManager.getDefaultUri(
            if (useNotificationSound) RingtoneManager.TYPE_NOTIFICATION else RingtoneManager.TYPE_RINGTONE
        )
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        val candidates = listOfNotNull(
            customUri?.let { it to "custom" },
            defaultUri?.let { it to "default ringtone" },
            alarmUri?.let { it to "alarm" }
        )
        playCandidate(candidates, 0, looping = settings.overrideSoundType != AlertSettingsRepository.SOUND_TYPE_NOTIFICATION)
    }

    private fun playCandidate(
        candidates: List<Pair<Uri, String>>,
        index: Int,
        looping: Boolean,
        onCompletion: (() -> Unit)? = null,
        onAllFailed: (() -> Unit)? = null
    ) {
        if (index >= candidates.size) {
            Timber.tag(TAG).e("Failed to play override sound: all URIs failed")
            onAllFailed?.invoke()
            return
        }

        val (uri, label) = candidates[index]
        Timber.tag(TAG).d("Override sound URI: $uri ($label)")

        val vol = 1f
        val mp = MediaPlayer()
        try {
            mp.setDataSource(this, uri)
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.isLooping = looping
            mp.setVolume(vol, vol)
            mp.setOnPreparedListener { player ->
                if (mediaPlayer !== player) return@setOnPreparedListener
                try {
                    player.start()
                    Timber.tag(TAG).i("Override sound playing on ALARM stream.")
                } catch (e: Exception) {
                    Timber.tag(TAG).e("Failed to start prepared override sound: ${e.message}")
                }
            }
            mp.setOnCompletionListener { player ->
                if (mediaPlayer === player) onCompletion?.invoke()
            }
            mp.setOnErrorListener { player, what, extra ->
                Timber.tag(TAG).e("MediaPlayer error preparing override sound: what=$what extra=$extra")
                if (mediaPlayer === player) {
                    mediaPlayer = null
                    try { player.release() } catch (_: Exception) {}
                    Timber.tag(TAG).w("Override sound ($label) failed asynchronously, advancing fallback chain.")
                    playCandidate(candidates, index + 1, looping, onCompletion, onAllFailed)
                } else {
                    try { player.release() } catch (_: Exception) {}
                }
                true
            }
            mp.prepareAsync()
            mediaPlayer = mp
        } catch (e: Exception) {
            Timber.tag(TAG).w("Failed to play URI: ${e.message}")
            try { mp.release() } catch (_: Exception) {}
            playCandidate(candidates, index + 1, looping, onCompletion, onAllFailed)
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun startVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }

            // Pattern: wait 0ms, vibrate 500ms, pause 500ms — repeating
            val pattern = longArrayOf(0, 500, 500)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            Timber.tag(TAG).i("Vibration started.")
        } catch (e: Exception) {
            Timber.tag(TAG).e("Failed to start vibration: ${e.message}")
        }
    }

    private fun stopRingtoneAndVibration() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
            } catch (e: Exception) {
                Timber.tag(TAG).e("Error stopping ringtone: ${e.message}")
            } finally {
                try {
                    it.release()
                } catch (e: Exception) {
                    Timber.tag(TAG).e("Error releasing ringtone: ${e.message}")
                }
            }
        }
        mediaPlayer = null

        vibrator?.cancel()
        vibrator = null
    }

    @Suppress("DEPRECATION")
    private fun restoreAudio() {
        if (!isOverriding) return

        overrideHandler.removeCallbacksAndMessages(null)

        Timber.tag(TAG).d("Restoring state: alarmVol=$savedAlarmVolume, dnd=$savedDndFilter")

        // 1. Stop our sound and vibration
        stopRingtoneAndVibration()

        try {
            // 2. Restore the only stream modified by CoreAlert playback.
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVolume, 0)
            Timber.tag(TAG).d("Alarm volume restored to $savedAlarmVolume")

            // Restore DND synchronously (no ringer-mode change happens anymore)
            if (notificationManager.isNotificationPolicyAccessGranted) {
                notificationManager.setInterruptionFilter(savedDndFilter)
                Timber.tag(TAG).d("DND restored to $savedDndFilter")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e("Error restoring audio state: ${e.message}", e)
        }

        isOverriding = false
        voipCallActive = false
        overrideKind = null
        overrideState.endOverride()
        notificationManager.cancel(OVERRIDE_NOTIFICATION_ID)
        currentRingingNumber = null
    }

    private enum class OverrideKind {
        CALL,
        MESSAGE
    }

    private fun createNotificationChannels() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_service_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notif_service_channel_desc)
        }

        val overrideChannel = NotificationChannel(
            OVERRIDE_CHANNEL_ID,
            getString(R.string.notif_override_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.notif_override_channel_desc)
        }

        notificationManager.createNotificationChannel(serviceChannel)
        notificationManager.createNotificationChannel(overrideChannel)
    }

    private fun buildPersistentNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent().setClassName(packageName, MAIN_ACTIVITY_CLASS),
            PendingIntent.FLAG_IMMUTABLE
        )

        val count = contactRepository.getContacts().size
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_service_title))
            .setContentText(getString(R.string.notif_service_text, count))
            .setSmallIcon(R.drawable.ic_notification_sos)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    fun refreshNotification() {
        if (!::notificationManager.isInitialized) return
        try {
            notificationManager.notify(NOTIFICATION_ID, buildPersistentNotification())
        } catch (e: SecurityException) {
            Timber.tag(TAG).e("Cannot refresh notification: ${e.message}")
        }
    }

    private fun buildOverrideNotification(): Notification {
        return NotificationCompat.Builder(this, OVERRIDE_CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_override_title))
            .setContentText(getString(R.string.notif_override_text))
            .setSmallIcon(R.drawable.ic_notification_sos)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
    }
}
