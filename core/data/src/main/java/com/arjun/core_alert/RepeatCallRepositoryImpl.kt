package com.arjun.core_alert

import android.os.SystemClock
import android.provider.Settings
import com.arjun.core_alert.models.CallAlertDecision
import com.arjun.core_alert.models.CallAlertMode
import com.arjun.core_alert.models.VipContact
import com.arjun.core_alert.policy.AlertGatePolicy
import com.arjun.core_alert.policy.QuietHoursPolicy
import com.arjun.core_alert.policy.RepeatCallTracker
import com.arjun.core_alert.util.PhoneUtils
import java.security.MessageDigest
import kotlin.math.abs
import org.json.JSONObject

internal class RepeatCallRepositoryImpl(
    source: PrefsDataSource,
    private val settings: AlertSettingsRepository,
    phoneBusy: Boolean
) : RepeatCallRepository {
    private val storage = source.callAttempts
    private val boot = Settings.Global.getInt(source.context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    private val tracker = RepeatCallTracker()
    private var checkedElapsed = SystemClock.elapsedRealtime()
    private var checkedWall = System.currentTimeMillis()
    override val busy: Boolean get() = tracker.busy
    private val windowMs: Long get() = settings.repeatCallWindowMinutes * 60_000L

    init {
        val saved = runCatching { JSONObject(storage.getString("state", "{}") ?: "{}") }.getOrDefault(JSONObject())
        if (boot >= 0 && saved.optInt("boot", -2) == boot) {
            checkedElapsed = saved.optLong("elapsed", checkedElapsed)
            checkedWall = saved.optLong("wall", checkedWall)
            tracker.restore(saved, SystemClock.elapsedRealtime(), windowMs, phoneBusy)
        } else {
            tracker.restore(JSONObject(), checkedElapsed, windowMs, phoneBusy)
        }
        checkGates()
        save()
    }

    override fun onRinging(contact: VipContact?): CallAlertDecision? {
        val allowed = checkGates()
        val mode = contact?.callAlertMode?.takeUnless { it == CallAlertMode.INHERIT } ?: settings.callAlertMode
        val key = contact?.let {
            MessageDigest.getInstance("SHA-256").digest(PhoneUtils.normalize(it.number).toByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
        }
        val result = tracker.onRinging(key, allowed, checkedElapsed, windowMs, mode, settings.volumePercent, settings.escalateCallVolume)
        save()
        return result
    }

    override fun onAnswered() { checkGates(); tracker.onAnswered(); save() }
    override fun onIdle() { checkGates(); tracker.onIdle(); save() }
    override fun reset() { tracker.resetHistory(); save() }

    private fun checkGates(): Boolean {
        val now = SystemClock.elapsedRealtime()
        val wall = System.currentTimeMillis()
        val allowed = AlertGatePolicy.allowsCall(settings.isServiceEnabled, settings.isMuted, settings.isInQuietPeriod())
        val clockChanged = now < checkedElapsed || abs((wall - checkedWall) - (now - checkedElapsed)) > 2_000L
        if (!allowed || clockChanged || now - checkedElapsed >= windowMs ||
            QuietHoursPolicy.wasQuietBetween(settings.getQuietRules(), checkedWall, wall)) tracker.resetHistory()
        tracker.prune(now, windowMs)
        checkedElapsed = now
        checkedWall = wall
        return allowed
    }

    private fun save() {
        storage.edit().putString("state", tracker.snapshot().put("boot", boot)
            .put("elapsed", checkedElapsed).put("wall", checkedWall).toString()).apply()
    }
}
