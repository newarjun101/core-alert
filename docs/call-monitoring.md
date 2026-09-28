# Call monitoring & sound override

This is the feature the app exists for: when a whitelisted VIP contact calls you — on a
cellular line or through a messenger (Viber / WhatsApp / Telegram) — or messages you, the
phone becomes audible **even in Silent, Vibrate or Do Not Disturb mode**, and afterwards
the exact previous state is restored.

The service layer lives in `core/monitoring/src/main/java/com/arjun/core_alert/`
(`CallMonitorService`, both receivers, the notification listener), with the persistence
it uses in `core/data/...` and the pure policies in `core/policy/...`.

---

## 1. How the override works (the key idea)

Android routes ringtone playback through the **ringer mode** and the **ring stream**, both
of which are suppressed by Silent mode and Do Not Disturb. CoreAlert instead plays the
alert on the **alarm stream** (`AudioAttributes.USAGE_ALARM`), which bypasses DND on every
device, and explicitly lifts the DND interruption filter for the duration of the alert.

Consequences of this design:

- **Ringer mode is never modified.** Only two things change:
  1. `STREAM_ALARM` volume,
  2. `NotificationManager.getInterruptionFilter()` (DND).
- Both are captured *before* the change and restored afterwards — including after a crash.
- If the phone would ring anyway (normal ringer, ring volume > 0, DND = ALL), the override
  is **skipped entirely** so you don't get a doubled ringtone
  (`AudioOverridePolicy.shouldOverride`).

```kotlin
// AudioOverridePolicy.kt
fun shouldOverride(ringerMode: Int, ringStreamVolume: Int, interruptionFilter: Int): Boolean {
    val ringerAudible = ringerMode == RINGER_MODE_NORMAL && ringStreamVolume > 0
    val dndAllowsAll  = interruptionFilter == INTERRUPTION_FILTER_ALL
    return !ringerAudible || !dndAllowsAll
}
```

---

## 2. Enabling monitoring

```
Home screen service switch (HomeViewModel)
   → ServiceEnablePolicy.canEnable(phone, callLog, dnd, notif)     // all four required
   → settings.setServiceEnabled(true)
   → CallMonitorService.start(ctx)                               // startForegroundService
```

* `ServiceEnablePolicy.canEnable` requires **`READ_PHONE_STATE` + `READ_CALL_LOG` +
  DND access (`ACCESS_NOTIFICATION_POLICY`) + notification permission** — all four.
* Turning it off sets `service_enabled = false` and stops the service.
* The service is declared with `android:foregroundServiceType="specialUse"`
  (+ `FOREGROUND_SERVICE_SPECIAL_USE`) and posts its notification with
  `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE` — the only foreground type it ever uses.
* `BootReceiver` restarts the service on `BOOT_COMPLETED` **and** on
  `MY_PACKAGE_REPLACED` (an APK update kills the process and sticky services are *not*
  restarted), re-arming the mute alarm if it is still in the future.
* The service is `START_STICKY`; Android restarts it after normal process death. As a
  final safety net, `MainActivity.onResume()` restarts it whenever `service_enabled` is
  true but the service instance is gone — the switch reflects the preference, not the
  real service state.

Home shows a warning banner driven by `HomeWarningPolicy.decide(...)`, which picks exactly
one of (in priority order):

| Value | Condition |
|---|---|
| `PERMISSIONS_REVOKED` | a critical permission (`PermissionPolicy.criticalMissing`) is missing while enabled |
| `AUTO_REVOKE` | the app is on Android's restricted-auto-revoke list |
| `BATTERY_UNRESTRICTED_NEEDED` | battery optimisation exemption missing |
| `NONE` | service disabled, or everything healthy |

---

## 3. The phone-state decision path

`CallMonitorService` registers a dynamic receiver for `TelephonyManager.ACTION_PHONE_STATE_CHANGED`.

**Only broadcasts that carry `EXTRA_INCOMING_NUMBER` are processed.** Android also sends a
numberless copy and the ordering is unspecified, so ignoring them is deliberate.

| State | Handling |
|---|---|
| `RINGING` | if a *message* override is active, restore first → `contactRepository.findVipContact(number)` → `repeatCalls.onRinging(vip)` → if `decision.shouldRing && vip != null` → `overrideAudio(...)` |
| `OFFHOOK` (answered) | `repeatCalls.onAnswered()`; if a message override is active, restore; if a call override is active, stop the sound/vibration now (volume/DND are restored later on IDLE) |
| `IDLE` | `repeatCalls.onIdle()`; if a call override is active → `restoreAudio()` |

A call from a **non-VIP** number still marks the repeat-call tracker as `busy` (with a
`null` key) so it can't be mistaken for part of a VIP sequence, but produces no decision.

Logging: phone numbers are only ever logged as `SHA-256(number).take(8)`.

### 3.1 Starting an override

`beginAudioOverride(number, kind, volumePercent, exactCallVolume)`:

1. `if (isOverriding) return false` — only one override at a time.
2. Skip if `AudioOverridePolicy.shouldOverride(...)` says the phone is already audible.
3. Save `currentRingingNumber`, `savedAlarmVolume` (`STREAM_ALARM`),
   `savedDndFilter`; set `isOverriding = true`, `overrideKind = kind`; **persist** via
   `overrideState.beginOverride(savedAlarmVolume, savedDndFilter)`
   `override_active`, `saved_alarm_volume`, `saved_dnd_filter`.
4. DND: if notification-policy access is granted → `setInterruptionFilter(INTERRUPTION_FILTER_ALL)`;
   otherwise post a high-priority warning notification (id 3) instead of failing silently.
5. Volume: `AlertVolumePolicy.targetStreamVolume(...)` → `setStreamVolume(STREAM_ALARM, …)`.
6. `scheduleAlertGateCheck()`.
7. Any exception → `restoreAudio()` and return `false`.

### 3.2 Playing the sound

`startOverrideSound()` walks a **candidate chain**, advancing on both synchronous failure
and the async `MediaPlayer.OnErrorListener`, with stale-player identity guards so an old
player can't kill the new one:

**Calls** (`overrideSoundType` = ringtone, the default):

1. the contact's custom ringtone from the phonebook (`ContactRingtoneHelper`,
   fails soft without `READ_CONTACTS`),
2. the system default ringtone **or** the default notification tone if the user chose
   "notification" sound type,
3. the default alarm tone.

Looping is enabled for ringtone-type sounds and disabled for notification-type.

**Messages**: contact's custom sound (only if `message_sound_type == MESSAGE_SOUND_CONTACT`),
then default notification, then default alarm; never looping; hard 15 s timeout.

Player attributes: `USAGE_ALARM` + `CONTENT_TYPE_SONIFICATION`.

Vibration pattern: `longArrayOf(0, 500, 500)` repeating (`VIBRATE` permission).

### 3.3 The alert gate (continuous re-check)

`scheduleAlertGateCheck()` re-evaluates every **`ALERT_GATE_CHECK_MS = 1 000 ms`**:

* `AlertGatePolicy.allowsCall(monitoringEnabled, paused, quietHours)` for call overrides,
* `AlertGatePolicy.allowsMessage(..., messageSoundEnabled)` for message overrides,

and calls `restoreAudio()` the moment the gate closes. The gate is *also* checked when a
relevant preference changes (`callPreferencesListener`) and inside the notification
listener — so a mute, a quiet period starting, or switching monitoring off takes effect
even mid-alert.

### 3.4 Restoring state

`restoreAudio()`:

1. clear all `overrideHandler` callbacks,
2. stop and release the `MediaPlayer` and the vibrator,
3. `setStreamVolume(STREAM_ALARM, savedAlarmVolume)`,
4. restore `savedDndFilter` (only if DND access is still granted),
5. `isOverriding = false`, `overrideKind = null`,
   `overrideState.endOverride()`, cancel notification 2, `currentRingingNumber = null`.

### 3.5 Crash self-healing

If the process dies mid-override the saved values are still in preferences.
On the next `onCreate`, `restoreStaleOverrideState()` restores them into memory and calls
`restoreAudio()` only when `OverrideStatePolicy.shouldRestoreOnStart(persisted = true,
callStateIdle)` — i.e. only when the call has actually ended. If the call is still live
the restore is deferred to the `IDLE` transition.

---

### 3.6 Messenger (VoIP) calls

Messenger calls never touch `TelephonyManager`, so the phone-state receiver cannot see
them: they arrive as a **notification** instead. `VipMessageNotificationListener`
(needs **notification access**) recognises them and feeds the very same override:

```
notification from a supported package
   → VoipCallPolicy.isCallNotification(app, category, fullScreenIntent)
        category == "call"  or  notification.fullScreenIntent != null
   → caller numbers read from EXTRA_PEOPLE_LIST / EXTRA_PEOPLE / EXTRA_CALL_PERSON
        (only "tel:" URIs / digit strings are kept)
   → CallMonitorService.voipCallIncoming(appLabel, numbers)
        VoipCallPolicy.shouldRing(numbers, matchedVip)
           • a number that is not a VIP → skipped (same rule as a cellular call)
           • a number that is a VIP     → repeat-call attempt → CallAlertDecision
           • no number exposed at all   → rings at the global volume (unknown caller)
        AlertGatePolicy.allowsCall → beginAudioOverride → alarm-stream ring + vibration
notification removed (declined / call over / answered)
   → CallMonitorService.voipCallEnded → restoreAudio() + repeatCalls.onIdle()
```

* Declining, hanging up or answering removes the messenger's call notification — that is
  the stop signal. A 60 s `VOIP_RING_TIMEOUT_MS` safety net stops a ring whose
  notification never goes away.
* Any telephony state change during a VoIP ring clears `voipCallActive`, handing
  ownership of the audio state back to the phone-state path, so the two paths can never
  fight over the same override.
* For a call only the category and the `tel:` people extras are read — no message text,
  no conversation content.

---

## 4. Decision policies (reference)

| File | API | Semantics |
|---|---|---|
| `AlertGatePolicy.kt` | `allowsCall(monitoringEnabled, paused, quietHours)` | `enabled && !paused && !quiet` |
| | `allowsMessage(…, messageSoundEnabled)` | `allowsCall(...) && messageSoundEnabled` |
| `AlertVolumePolicy.kt` | `targetStreamVolume(max, current, percent, preserveHigherCurrentVolume)` | `percent` clamped **1..100**; result at least **1**; if `preserveHigherCurrentVolume`, returns `max(requested, current)` |
| `AudioOverridePolicy.kt` | `shouldOverride(ringerMode, ringVolume, filter)` | see §1 |
| `OverrideStatePolicy.kt` | `shouldRestoreOnStart(persisted, callIdle)` | `persisted && callIdle` |
| `MutePolicy.kt` | `isMuted(untilMs, nowMs)` | `untilMs != 0 && nowMs < untilMs` (strictly before — at the exact timestamp you are unmuted) |
| `ServiceEnablePolicy.kt` | `canEnable(phone, callLog, dnd, notif)` | all four |
| `PermissionPolicy.kt` | `criticalMissing(callLogOk, phoneStateOk, dndOk)` | list of `"READ_CALL_LOG"`, `"READ_PHONE_STATE"`, `"DND"` |
| `WarnThrottlePolicy.kt` | `shouldWarn(last, now)` | at most once every **24 h** (`INTERVAL_MS`) |
| `HomeWarningPolicy.kt` | `decide(serviceEnabled, criticalMissing, autoRevoke, battery)` | see §2 |

---

## 5. Repeat-call mode ("sound from the second call")

Configured in **Settings → When to sound for VIP calls** (or the call-mode summary button
on Home). Implemented by `RepeatCallPolicy.kt` (`:core:policy`) + `RepeatCallRepositoryImpl.kt` (data).

### 5.1 Modes and windows

```kotlin
enum class CallAlertMode { INHERIT, FIRST, SECOND }   // FIRST is the default
data class CallAlertDecision(val shouldRing: Boolean, val volumePercent: Int, val exactVolume: Boolean)

windowOptions = [3, 5, 10] minutes          // invalid values fall back to 5
```

* `FIRST` — ring on attempt ≥ 1 (default, unchanged behaviour).
* `SECOND` — stay silent on attempt 1, ring from attempt 2 within the window.
* Each VIP row can override the global setting or `INHERIT` it.

### 5.2 What counts as an attempt

`RepeatCallTracker` models **one aggregate cellular call session**:

* Separate incoming **cellular** calls count, and so do **VoIP calls from a VIP number**
  (same key, see §3.6) — messages, outgoing calls, call waiting and VoIP calls from an
  unknown number never count.
* The window is **anchored to the first call and does not slide**; a call at exactly
  `start + windowMs` is already expired.
* Duplicate `RINGING` broadcasts for the same ring are suppressed by a `claimed` guard.
* A null/unknown caller number never counts; different numbers never combine.
* Count is **capped at 4**.
* An unanswered or rejected call counts; **answering resets that caller's sequence**
  (`onAnswered` deletes only that key's history).
* Non-VIP calls mark the tracker busy with a `null` key, so they can't be absorbed into a
  VIP sequence.

### 5.3 Resets

History is discarded when:

* the gate fails (service off / muted / quiet hours),
* any call-related preference changes — `PrefsDataSource.put(…, callScoped = true)` also
  **clears the whole `corealert_call_attempts` preferences file**,
* the clock changed (elapsed-vs-wall skew > **2 000 ms**, or wall clock went backwards),
* the elapsed gap since the last check ≥ the window,
* a quiet period occurred between the previous and current check
  (`QuietHoursPolicy.wasQuietBetween`),
* pausing monitoring.

Additionally `resetHistory()` while a call is in progress sets `claimed = true`, so
changing a setting mid-call cannot re-arm the call already ringing.

### 5.4 Persistence & reboot behaviour

Stored in SharedPreferences `"corealert_call_attempts"`:

```json
{
  "history": [ { "key": "<64 hex = SHA-256(normalized VIP number)>",
                 "start": <SystemClock.elapsedRealtime()>,
                 "count": 1..4 } ],
  "active":  { ... },
  "boot":    <Settings.Global.BOOT_COUNT>,
  "elapsed": <monotonic ms of last check>,
  "wall":    <wall ms of last check>
}
```

* **Reboot:** `boot` mismatch ⇒ everything discarded (sequences do not survive reboot).
* **Process death:** the `active` key is dropped on restore — *"if the process died during
  a call, its outcome is unknown: never infer a missed call."* A completed missed call
  that was already committed to `history` survives.
* Restore validates `key` against `[a-f0-9]{64}`, `count ∈ 1..4`, `start >= 0`, and caps
  the list at **1000** rows.
* **Never exported:** the configuration export contains no `history` key (asserted by
  `RepeatCallConfigTest`).

### 5.5 Optional volume escalation

Setting: **"Double the volume on subsequent calls"**, default **off**.

```kotlin
base      = baseVolume.coerceIn(25, 100)          // call volume range is 25..100
progressive = escalate && base < 50
firstAudible = if (mode == SECOND) 2 else 1
volume = base; repeat((attempt - firstAudible).coerceIn(0, 2)) { volume = min(volume*2, 100) }
```

* Only applies when the configured base is **below 50 %**.
* At most **two doublings**, capped at 100 %.
* Examples: base 25 → `25 → 50 → 100 → 100`; base 45 → `45 → 90 → 100`; base ≥ 50 → unchanged.
* When escalation is active `exactVolume = true`, which makes `AlertVolumePolicy` force
  the exact level instead of preserving a higher current system alarm volume.
* The volume changes **between** calls, not gradually during one, and the original alarm
  volume is restored after each call.
* The count starts with *the first call allowed to sound* (i.e. the 2nd real call in
  second-call mode).

Messages never advance the call counter and keep their own volume settings.

---

## 6. Quiet hours

`QuietHoursPolicy.kt` + `QuietRulePolicy.kt`, configured in Settings.

```kotlin
data class QuietRule(days: Set<Int>, startHour, startMinute, endHour, endMinute)
// days are Calendar.DAY_OF_WEEK values (SUNDAY=1 … SATURDAY=7)
MAX_QUIET_RULES = 10
```

* Validation: at least one day **and** start ≠ end
  (`QuietRulePolicy.isValid`). Range checks (hours 0–23, minutes 0–59, days 1..7) live
  in `ConfigExporter.isValidRuleRanges` and are re-applied on import.
* Same-day rule (`end > start`): quiet iff `start <= minuteOfDay < end`
  (**start inclusive, end exclusive**).
* Cross-midnight rule (`end <= start`): `(day ∈ days && minute >= start) ||
  (previousDay ∈ days && minute < end)` — so a Friday 22:00–06:00 rule covers Friday night
  and Saturday morning.
* `wasQuietBetween(rules, from, to)` walks minute-by-minute and is used to decide whether
  to reset pending repeat-call sequences. It returns `true` **conservatively** when
  `to < from` or the span exceeds **10 minutes** (protects against long gaps between gate
  checks).
* Adding a rule while currently inside a quiet period immediately suspends any active alert.
* Quiet hours apply to **both calls and messages** (they feed `AlertGatePolicy`).

---

## 7. Mute timer

Temporary global mute, 1–12 hours, available from Home.

* UI: `NumberPicker` 1..12 → `muteUntil = now + hours * 3_600_000L`.
* On set: write `mute_until_timestamp`, `CallMonitorService.getInstance()?.suspendActiveAlert()`,
  `BootReceiver.scheduleMuteAlarm(...)`.
* Scheduling: `AlarmManager.RTC_WAKEUP` + `setExactAndAllowWhileIdle`; on API 31+, falls
  back to `setAndAllowWhileIdle` when `canScheduleExactAlarms()` is false.
  `PendingIntent` → `MuteTimerReceiver` with action `com.arjun.core_alert.ACTION_UNMUTE`
  (request code 0, `FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE`).
* Expiry: `MuteTimerReceiver` just calls `settings.clearMute()`; the 1 s gate check and
  the preference listener pick it up (it does not call into the service directly).
* Cancel: set to 0 and cancel the alarm.
* Reboot: `BootReceiver` re-schedules if the timestamp is still in the future.
* UI countdown re-posts every 60 s; `MutePolicy.isMuted` is strictly `now < until`, and the
  getter lazily self-clears an expired value.
* Mute applies to calls **and** messages.

---

## 8. Notifications

### Channels

| Channel id | Importance | Content |
|---|---|---|
| `corealert_channel` | LOW | persistent foreground-service notification: *"CoreAlert active — monitoring N VIP contacts"* |
| `corealert_override` | HIGH | *"Ringtone forced!"*, DND-degraded warning, permission-revoked warning |

### Notification ids

| ID | Content | Source |
|---|---|---|
| 1 | persistent FGS notification | `CallMonitorService` |
| 2 | "Ringtone forced!" (override active) | `CallMonitorService`, posted on call override, cancelled in `restoreAudio` |
| 3 | DND permission degraded warning | `CallMonitorService` (`OVERRIDE_NOTIFICATION_ID + 1`) |
| 5 | permission-revoked warning | `CallMonitorService.PERM_WARNING_NOTIFICATION_ID` |

### Actions / intents

| Action | Direction |
|---|---|
| `android.intent.action.BOOT_COMPLETED`, `android.intent.action.MY_PACKAGE_REPLACED` | system → `BootReceiver` (permission-guarded) |
| `com.arjun.core_alert.ACTION_UNMUTE` | AlarmManager → `MuteTimerReceiver` |
| `com.arjun.core_alert.MESSAGE_VIP_ALERT` + extra `vip_number` | notification listener → `CallMonitorService` (explicit intent; also used to cold-start the FGS) |
| `TelephonyManager.ACTION_PHONE_STATE_CHANGED` | system → dynamic receiver in the service |

---

## 9. Key classes at a glance

| File | Role | ~LOC |
|---|---|---|
| `CallMonitorService.kt` | foreground service, receiver, override engine, gate, message + VoIP call override | 670 |
| `RepeatCallPolicy.kt` | `RepeatCallPolicy`, `RepeatCallTracker` (`CallAlertMode` / `CallAlertDecision` live in `domain/models/`) | 118 |
| `RepeatCallRepositoryImpl.kt` | persistence + gating wrapper around the tracker (`RepeatCallRepository`) | 75 |
| `QuietHoursPolicy.kt` | quiet-period evaluation | 40 |
| `AlertSettingsRepositoryImpl.kt` | all alert preferences + the change listener (see [persistence.md](persistence.md)) | 177 |
| `PhoneUtils.kt` | number normalisation and matching | 30 |
| `ContactRingtoneHelper.kt` | per-contact custom ringtone lookup | 45 |
| `BootReceiver.kt` | boot / package-replace restart + mute alarm scheduling | 76 |
| `MuteTimerReceiver.kt` | mute expiry | 24 |
| `feature/home/HomeScreen.kt` | Home UI: hero card + service switch, contacts, mute, warnings | 880 |
| `HomeWarningPolicy.kt`, `ServiceEnablePolicy.kt`, `PermissionPolicy.kt`, `WarnThrottlePolicy.kt` | small pure decision helpers | ≤ 20 each |

---

## 10. Number matching (`PhoneUtils`)

VIP matching must work for numbers stored in different formats, which is why
`findVipContact`/`matches` exist rather than plain string equality.

`normalize`:

* strips `space`, `-`, `(`, `)`, `.`
* converts a leading `00` to `+`
* Italian heuristics: leading `39` with length ≥ 11 → `+39…`; a leading `3` with length 10
  and second digit not `0`/`1` → prefix `+39`

`matches(a, b)`:

1. exact normalised equality, else
2. compare digit forms (raw and leading-zero-stripped), with suffix matching that requires
   **at least 8 digits** compared on equal-length tails.

Short numbers therefore never suffix-match. Covered by `PhoneUtilsTest` (18 tests).

---

## 11. Edge cases explicitly handled

* Android's second, numberless `PHONE_STATE` broadcast and unspecified ordering.
* A call ending between the telephony snapshot and receiver registration (re-checked
  immediately after registration).
* Process death mid-override → persisted `override_active` + saved volume/filter.
* Process death mid-ring → `busy/ringing/claimed` reconstructed from live `callState`.
* Duplicate `RINGING` broadcasts, call waiting, outgoing calls, cross-number mixing.
* Clock/timezone changes (elapsed vs wall skew > 2 s).
* Async `MediaPlayer` errors advancing the fallback chain; stale-player identity guards.
* Message alerts hard-timeout after 15 s even if playback hangs.
* Message alerts suppressed during any cellular call.
* Custom ringtone resolution failing soft without `READ_CONTACTS`.
* Permission warning notification throttled to once per 24 h.
