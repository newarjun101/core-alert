# VIP message alerts

Optional feature: make a **direct** message from a VIP contact audible even when the phone
is silent, for **WhatsApp**, **Google Messages (SMS/RCS)**, **Telegram** and **Viber**.

Implemented as a `NotificationListenerService` that inspects incoming conversation
notifications, decides whether they belong to a paired VIP, and hands the alert to
`CallMonitorService` (which plays it on the alarm stream exactly like a VIP call).

The same listener also recognises **incoming messenger calls** (`category == "call"`) and
triggers the call override — see `call-monitoring.md` §3.6.

Relevant files:

| File | Role |
|---|---|
| `monitoring/VipMessageNotificationListener.kt` | the `NotificationListenerService`: messages + VoIP call detection (203 LOC) |
| `policy/VipMessageNotificationPolicy.kt` | pure filter + hashing helpers |
| `policy/VoipCallPolicy.kt` | call-notification detection + VoIP ring decision |
| `domain/MessageBindingRepository.kt` | the repository interface the listener and provider depend on |
| `data/MessageBindingRepositoryImpl.kt` | pairing state + dedup fingerprints (189 LOC) |
| `monitoring/VipMessageAlertsProvider.kt` | injectable façade (`single` in `monitoringModule`), injected into `HomeViewModel` / `SettingsViewModel` |
| `domain/models/` | `MessageApp.kt`, `MessageAlertState.kt`, `PendingMessagePairing.kt` — one type per file |
| `monitoring/GoogleMessagesVipResolver.kt` | auto-pair for Google Messages sender URIs |

The listener is declared in the **main** manifest, so the feature ships in both flavors.

---

## 1. Enabling

1. Settings → **Message alerts**: master switch `message_sound_enabled` (default **on**),
   volume slider `message_volume_percent` (5–100, default 100), sound type radio
   (`MESSAGE_SOUND_DEFAULT` / `MESSAGE_SOUND_CONTACT`).
2. The app needs **notification access** (`NotificationListenerService`); the UI deep-links
   to `ACTION_NOTIFICATION_LISTENER_SETTINGS`.
   `VipMessageAlertsProvider.hasNotificationAccess` checks
   `NotificationManagerCompat.getEnabledListenerPackages`.
3. Per contact, via the VIP row menu → **Message alerts** → pick the app → pairing starts.

---

## 2. Supported apps

```kotlin
// domain/models/MessageApp.kt
enum class MessageApp(val storageId: String) {
    WHATSAPP("whatsapp"),
    GOOGLE_MESSAGES("google_messages"),
    TELEGRAM("telegram"),
    VIBER("viber")
}
```

Package resolution (`VipMessageNotificationPolicy`):

| Package | App |
|---|---|
| `com.whatsapp` | WhatsApp |
| `com.google.android.apps.messaging` | Google Messages |
| `org.telegram.messenger`, `org.telegram.messenger.web` | Telegram |
| `com.viber.voip` | Viber |

`enum MessageAlertState { UNPAIRED, PAIRING, PAIRED }`.

---

## 3. What gets inspected

`VipMessageNotificationPolicy.shouldInspect(app, isGroupSummary, isGroupConversation,
category, conversationId, messageCount)` returns true only when **all** hold:

* the package is one of the four supported apps,
* it is **not** a group summary notification,
* it is **not** a group conversation,
* `category` is `null` or `"msg"` — a `"call"` category is rejected, so WhatsApp/Telegram
  *call* notifications never trigger a sound alert,
* `conversationId` (from `shortcutId`, falling back to `sbn.tag`) is non-blank,
* `messageCount > 0`.

Both the legacy `onNotificationPosted(sbn)` and the ranking overload funnel into one
`handleNotification`.

---

## 4. Pairing states

### 4.1 Explicit pairing (all four apps)

User taps **Pair** on a VIP row → `MessageBindingRepository.beginPairing(number, app)` stores
`pending_number` + `pending_app`. The next qualifying notification from that app:

* if no VIP contact matches → cancel pairing and stop,
* **Google Messages extra check**: if candidate numbers were extracted and *none* matches
  the pending VIP → ignore it and keep waiting (don't bind to the wrong conversation),
* otherwise `bind(number, app, conversationHash, initialEventFingerprint)` and show
  *"Paired"* (`message_pair_success`). The **pairing notification itself does not fire an
  alert**, because the event fingerprint is seeded at bind time.

### 4.2 Auto-pairing (Google Messages only)

If there is **no** binding for that conversation and the notification exposes sender
people URIs:

```
GoogleMessagesVipResolver.candidateNumbers(...)   // MessagingStyle senderPerson + EXTRA_PEOPLE_LIST
        → numbersForUri: "tel:" scheme | content:// contact lookup (needs READ_CONTACTS)
GoogleMessagesVipResolver.uniqueVip(numbers, contacts)   // must match EXACTLY one VIP
        → bind(...) + toast "message_auto_paired"   // and the alert fires immediately
```

0 matches or ≥ 2 matches ⇒ **no** auto-pair (conservative).

### 4.3 Matched / paired

`contactForConversation(app, conversationHash, contacts)` reverse-looks up the VIP list,
then `isNewEvent(app, conversationHash, fingerprint)` does a compare-and-swap: equal
fingerprint ⇒ duplicate, ignored; different ⇒ store the new one and alert.

### 4.4 Unpairing

* Per app: removes the binding, removes the event fingerprint **only when no other contact
  shares that conversation**, and cancels a matching pending pair.
* `unpairAll(number)`: all four apps + pending.
* `migrateNumber(old, new)`: re-keys bindings and pending when a VIP number is edited.
* Legacy migration: a one-shot `legacy_whatsapp_migrated` flag re-keys pre-existing
  bindings stored under the old `whatsapp_vip_alerts` file into the
  `whatsapp|<normalizedNumber>` scheme.

---

## 5. Privacy: what is stored

Nothing readable about you or your contacts is persisted from notifications.

| Stored | Format |
|---|---|
| binding key | `"<app.storageId>\|<normalizedNumber>"` → conversation hash |
| conversation hash | `sha256(conversationId)` — 64 hex chars, never contains the raw id |
| event fingerprint | `sha256("$conversationId\|$latestMessageTime\|$messageCount")` — used for dedup only |
| pending pair | normalized phone number + app id (only while pairing is in progress) |

**Never stored:** notification message text, contact names, or phone numbers extracted
from notifications. `Notification.EXTRA_MESSAGES` is read transiently to obtain the
latest timestamp and count, then discarded.

Timestamps are taken as: max of per-message `"time"` if > 0 → else `notification.when` if
> 0 → else `sbn.postTime`.

Diagnostics/verbose logging only when `BuildConfig.DEBUG` or `Log.isLoggable(TAG, DEBUG)`.
Listener tag: `CoreAlertMessages` (emitted as `Timber.tag(TAG).i(...)`, gated as shown).

---

## 6. Alert delivery

```
VipMessageNotificationListener.playAlertIfAllowed(contact)
   → AlertGatePolicy.allowsMessage(serviceEnabled, isMuted, isInQuietPeriod, messageSoundEnabled)
        — false ⇒ log and skip
   → CallMonitorService.playMessageAlert(context, contact.number)
        — explicit intent ACTION_MESSAGE_ALERT + EXTRA_VIP_NUMBER "vip_number"
        — if the FGS instance exists, calls it directly; otherwise starts the FGS
```

Inside `CallMonitorService.overrideMessageAlert(number)`:

1. **Abort if a cellular call is ringing or in progress**
   (`repeatCalls.busy || !isCallStateIdle()`) — message sound is suppressed during calls.
2. Abort unless `AlertGatePolicy.allowsMessage(...)` passes.
3. `beginAudioOverride(number, MESSAGE, messageVolumePercent)`:
   * `preserveHigherCurrentVolume` is **false** for messages, so the exact configured
     message volume is forced (it may lower the alarm stream below the current level).
4. Sound chain: contact custom sound (only if `MESSAGE_SOUND_CONTACT`) → default
   notification → default alarm; **never loops**.
5. Restore on playback completion, on all-candidates-failed, or after the hard
   `MESSAGE_ALERT_TIMEOUT_MS = 15_000` timeout.
6. No "override active" notification (id 2) is posted for messages — only calls post it.

Message alerts also feed the same 1-second alert gate, so mute/quiet/service-off stops
them mid-sound.

---

## 7. Interaction with other features

* **Mute timer** and **quiet hours** apply (both feed `AlertGatePolicy`).
* **Repeat-call mode** is unaffected: messages never advance the call counter and keep
  their own volume and sound settings.
* **During a cellular call:** message override is stopped/restored; message alerts are not
  started.
* **Contact edits:** `migrateNumber` re-keys bindings; the repository alert-change listener
  resets repeat-call state but not message bindings.
* **Backup export:** message bindings are *not* included in configuration exports.

---

## 8. Tests

| Test file | Verifies |
|---|---|
| `policy test/VipMessageNotificationPolicyTest.kt` (5) | package map, group-summary / group-conversation / `category="call"` rejection, conversation hash has no raw id, fingerprint format |
| `monitoring test/GoogleMessagesVipResolverTest.kt` (2) | exactly-one-VIP requirement for auto-pairing |
| `policy test/AlertGatePolicyTest.kt` (5) | the message gate vs the call gate |
