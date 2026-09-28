# Security & privacy

CoreAlert makes strong, specific claims about what it does and does not do with your data.
This document states the permission model, the cryptographic construction, the threat model
and the data-handling rules, based on the code at `versionCode` 51 / `2.22.0`.

---

## 1. Stated guarantees

From `core/ui/src/main/res/values/strings.xml` (`privacy_data_body`), the app's own privacy
screen:

* All call, contact and notification processing happens **on-device**.
* Notification message text, contact names and phone numbers extracted from notifications
  are **never stored** — only hashed conversation ids and dedup fingerprints.
* *"Call monitoring runs entirely on your phone: it never uploads your contacts, call
  history or location, and nothing is sent to a server."*
* A backup you export is **a local file encrypted with a password you choose**; the app
  never sees that password.
* *"CoreAlert does not protect against a compromised phone, malware, screenshots, or
  keyloggers."*
* No analytics, no tracking, no ads.
* **100% offline** — no Firebase, no analytics/advertising SDKs, no third-party scripts, no
  push notifications and **no `INTERNET` permission at all**. The APK contains no HTTP
  client, and neither flavor ever opens a network connection: the app cannot generate any
  network traffic whatsoever.

---

## 2. Permissions

Declared in `app/src/main/AndroidManifest.xml` (10 permissions):

| Permission | Why it is needed | Required to enable monitoring? |
|---|---|---|
| `READ_PHONE_STATE` | detect incoming call state transitions | **yes** |
| `READ_CALL_LOG` | obtain the incoming number (Android 10+ hides it from `PHONE_STATE` alone) | **yes** |
| `ACCESS_NOTIFICATION_POLICY` | lift/restore the DND interruption filter | **yes** |
| `POST_NOTIFICATIONS` | persistent service notification (Android 13+) | **yes** |
| `MODIFY_AUDIO_SETTINGS` | set/restore `STREAM_ALARM` volume | implicit |
| `VIBRATE` | force vibration during a VIP call | implicit |
| `READ_CONTACTS` | pick VIP contacts; per-contact custom ringtone; Google Messages sender resolution | no |
| notification access (`NotificationListenerService`) | identify paired direct conversations; detect incoming messenger calls | only for message alerts + VoIP call detection |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | keep monitoring alive (`specialUse` type) | implicit |
| `RECEIVE_BOOT_COMPLETED` | auto-start after reboot | implicit |

`ServiceEnablePolicy.canEnable(phone, callLog, dnd, notif)` requires the first four — you
cannot turn monitoring on without them.

There is **no** `INTERNET` permission and no `<uses-permission>` of any network kind, no
`<provider>` entry and no `REQUEST_INSTALL_PACKAGES`; there is no `<uses-feature>`
declaration of any kind.

### 2.1 Background restrictions

* The core service is a **`specialUse`** foreground service with an explicit
  `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` justification string:
  *"Monitor incoming calls from VIP contacts to override silent/DND mode for emergencies"*.
* It is started with `startForeground(id, notification,
  FOREGROUND_SERVICE_TYPE_SPECIAL_USE)` and never changes type afterwards.
* The app declares no location permissions, so none of the location-related background
  restrictions apply to it.

---

## 3. Cryptographic inventory

| Purpose | Algorithm / construction | Key material | Location |
|---|---|---|---|
| Config backup export | PBKDF2-HmacSHA256 (**600 000** iters, 16-byte salt) → AES-256-GCM (12-byte IV, 128-bit tag) | user password | `ConfigCrypto` |
| Conversation / number hashing | SHA-256 (hex) | — | `VipMessageNotificationPolicy`, `RepeatCallRepositoryImpl` |

Notes on the constructions:

* **The backup envelope is the only encryption in the app.** It is
  `{format: "corealert-enc-v1", iter, salt, iv, data}`; `decrypt` returns `null` for a
  wrong format, an out-of-range iteration count or a bad password — there is no way to
  tell *why* it failed.
* **Encrypted-envelope iteration bounds** on import (100 000 … 1 000 000) prevent a
  malicious backup from forcing an unbounded PBKDF2 loop, and stop a downgrade to a
  trivially cheap KDF.
* **Unencrypted imports are rejected** before any prompt: `isEncryptedEnvelope` must be
  true or the file is refused.
* **The app holds no long-lived secret of its own.** There is no keyring, no keystore
  alias, no stored token — the only secret material is the backup password, which stays
  with the user.
* **Hashes, not values**, are the rule everywhere else: repeat-call history keys are
  `SHA-256(normalised number)` hex, conversation ids are `SHA-256(conversationId)`, dedup
  fingerprints are `SHA-256(id|time|count)`, and log lines only ever contain
  `SHA-256(number).take(8)`.

---

## 4. Threat model

### 4.1 What the design defends against

| Threat | Mitigation |
|---|---|
| The app phoning home | no `INTERNET` permission, no HTTP client — the process cannot open a socket |
| Backup exfiltration of settings | `allowBackup="false"` plus explicit `<cloud-backup>`/`<device-transfer>` exclusions for `corealert_prefs` |
| A third party reading your config export | export is always password-encrypted (PBKDF2 600 k + AES-GCM); legacy unencrypted imports are rejected |
| An exported component being abused | services are `exported=false`; `BootReceiver` is exported but guarded by `RECEIVE_BOOT_COMPLETED`; the message-alert intent is an explicit intent to our own service |
| Someone silently enabling your monitor | the enable gate requires four runtime permissions including DND access |
| Notification content being persisted | only conversation ids and fingerprints are stored, both SHA-256; message text is read transiently from `EXTRA_MESSAGES` and discarded |

### 4.2 What it explicitly does **not** defend against

Stated in the app's own privacy text:

* a compromised phone, malware, screenshots, keyloggers,
* physical access to an unlocked device (the app is not a secret store),
* a weak backup password — PBKDF2 raises the cost of guessing but cannot rescue a
  guessable password.

Additionally, worth knowing:

* **Notification access is broad.** The OS grants the listener visibility over all
  notifications; CoreAlert filters to the supported packages, stores hashes only, and for
  calls reads only the category plus the `tel:` people extras, but the permission itself
  is not limited to that.

### 4.3 Differences between flavors

There are none: the flavors differ only in application id (§architecture 2), so the
security posture described here applies identically to `production` and `uat` builds.

---

## 5. Data inventory

### 5.1 Never leaves the device

* Contacts, quiet rules, call settings, repeat-call sequences (also excluded from export).
* Notification text, contact names, numbers extracted from notifications.
* Message binding hashes and dedup fingerprints.
* The mute timer, saved override state and every other runtime state.

The only way any of it leaves is **you explicitly exporting a backup file** (§7).

### 5.2 Leaves the device

**Nothing.** The app has no `INTERNET` permission and no HTTP client, so there is no code
path that can send data anywhere. The only way data leaves the device is **you explicitly
exporting a backup file** yourself (§7).

No analytics, no telemetry, no crash reporting, no advertising SDKs in either flavor.

### 5.3 Pseudonymisation

* Logs: `SHA-256(number).take(8)` for call logs.
* Repeat-call history keys: full `SHA-256(normalised number)` hex — never the number.
* Conversations: `SHA-256(conversationId)`; dedup: `SHA-256(id|time|count)`.

---

## 6. Update mechanism

There is none. The app performs no version check, no download and no self-install — it
declares no `INTERNET` permission, ships no HTTP client, and its manifest has no
`FileProvider` / `REQUEST_INSTALL_PACKAGES` plumbing. New releases are installed like any
other Android package (sideload, or whatever channel you distribute through).

---

## 7. Backup & restore

* **Android auto-backup is off** (`allowBackup="false"`), and the rules files additionally
  exclude `corealert_prefs.xml` from both cloud backup and device-to-device transfer.
* **Manual export** (Settings → Backup): `corealert-backup-yyyyMMdd-HHmmss.json`, always
  encrypted with a user password (`ConfigCrypto`, `corealert-enc-v1` envelope).
  Exported content: contacts, quiet rules, sound/call/message settings, repeat-call
  settings, appearance — **not** repeat-call sequences, **not** message bindings, **not**
  the mute timer or saved override state.
* **Import:** legacy plain JSON is rejected; the envelope is decrypted off the UI thread;
  rule ranges are re-validated (`ConfigExporter.isValidRuleRanges`); after applying, the
  service is restarted if it was enabled. Unknown/legacy keys in an imported file are
  ignored rather than trusted.

---

## 8. Logging policy

* Facade: Timber 5.0.1 — `DebugTree` planted in `CoreAlertApp.onCreate()`, every call keeps its
  historical tag (`Timber.tag("CoreAlert")`, …). `android.util.Log`
  is otherwise unused; it is kept only for the `Log.isLoggable(...)` gates below.
* Default: minimal, no personally identifying content.
* Phone numbers: only `SHA-256(...).take(8)`.
* Notification contents: not logged unless `BuildConfig.DEBUG` or
  `Log.isLoggable("CoreAlertMessages", DEBUG)`.
* Backup decrypt failures are reported to the user as a generic failure — no file contents
  are echoed.
* Permission warnings are throttled to one per 24 h (`WarnThrottlePolicy`).

---

## 9. Licence & third-party assets

| Asset | Licence |
|---|---|
| CoreAlert source | **GPL-3.0-or-later**, `Copyright (C) 2026 Lorenzo Marci` + `Copyright (C) 2026 Arjun (github.com/newarjun101)` (`LICENSE`) |
| Manrope font (`res/font/`) | SIL OFL 1.1 (`licenses/Manrope-OFL.txt`) |
| AndroidX, Material | Apache-2.0 |

### 9.1 Credits

CoreAlert is a continuation of **[SOS Ring](https://github.com/JackRushante/SOSRing)** by
**JackRushante (Lorenzo Marci)** — thank you for the original app: the foreground
call-monitoring service, the DND override through the ALARM audio stream and the full state
restore after every alert come from that project.

The Compose migration, the CoreAlert rebrand and the new features were added by
**[Arjun](https://github.com/newarjun101)**
([repository](https://github.com/newarjun101/core-alert)).

**Contact** — for any help, or if you need any other app, I am always here to assist:

| | |
|---|---|
| Email | newarjun101@gmail.com |
| Phone / WhatsApp | +95 9 978770588 |
| GitHub | github.com/newarjun101 |
| Repository | github.com/newarjun101/core-alert |

The same credit and contact details are shown on the in-app **Privacy & licenses** screen
(`privacy_license_body`, `privacy_contact_body`) in `core/ui/src/main/res/values/strings.xml`
and its Burmese translation in `values-my/`.

