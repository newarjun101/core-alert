# Persistence reference

Every place CoreAlert stores state: SharedPreferences files, and the backup/export format.

---

## 1. SharedPreferences files

| File name | Owner | Contents |
|---|---|---|
| `corealert_prefs` | `PrefsDataSource` (read/written by the repository impls) | the main settings store |
| `corealert_call_attempts` | `RepeatCallRepositoryImpl` (cleared by `PrefsDataSource`) | in-flight repeat-call sequences (boot-scoped) |
| `vip_message_alerts` | `MessageBindingRepositoryImpl` | message pairing + dedup fingerprints |
| `whatsapp_vip_alerts` | legacy (read-only) | pre-migration WhatsApp bindings |

That is the complete list — there is no database and no other file-backed store.

---

## 2. `corealert_prefs` — full key list

Each key is a private constant of the repository that owns it, in module `core/data`:
`AlertSettingsRepositoryImpl` (calls, mute, quiet, message alerts, permission warning),
`ContactRepositoryImpl` (`vip_contacts`), `ThemeRepositoryImpl` (`theme_palette`,
`theme_mode`), `OverrideStateRepositoryImpl` (`override_*`, `saved_*`).

### 2.1 Contacts

| Key | Type | Default | Notes |
|---|---|---|---|
| `vip_contacts` | JSON array string | `[]` | objects `{name, number, ringtoneEnabled, callAlertMode}` |

```kotlin
data class VipContact(
    val name: String,
    val number: String,                        // normalised on write
    val ringtoneEnabled: Boolean = true,       // persisted & exported; not consulted by the ring flow
    val callAlertMode: CallAlertMode = CallAlertMode.INHERIT
)
```

Helpers on `ContactRepository`: `getContacts()`, `saveContacts()`, `getVipNumbers()`,
`findVipContact(incoming)` (the last two are interface defaults built on `PhoneUtils`).

### 2.2 Monitoring / calls  ⚠️ *these route through
`PrefsDataSource.put(key, value, callScoped = true)`, which also
**clears the entire `corealert_call_attempts` file*****

| Key | Type | Default | Range / notes |
|---|---|---|---|
| `service_enabled` | Boolean | `false` | master switch |
| `volume_percent` | Int | `100` | clamped **25..100** (`MIN_VOLUME_PERCENT`, `MAX_VOLUME_PERCENT`) |
| `call_alert_mode` | String enum | `null` → `FIRST` | `INHERIT` / `FIRST` / `SECOND`; getter maps `INHERIT` → `FIRST` |
| `repeat_call_window_minutes` | Int | `5` | validated to `{3,5,10}` else 5 |
| `escalate_call_volume` | Boolean | `false` | "double volume on subsequent calls" |
| `mute_until_timestamp` | Long | `0` | wall-clock end of the mute timer; 0 = not muted |
| `quiet_rules` | JSON array string | `[]` | objects `{days[], startHour, startMinute, endHour, endMinute}`; max `MAX_QUIET_RULES = 10` |

`override_sound_type` is the exception: it is written with `callScoped = false` and does
**not** invalidate repeat-call history.

| Key | Type | Default | Range / notes |
|---|---|---|---|
| `override_sound_type` | Int | `0` | `SOUND_TYPE_RINGTONE = 0`, `SOUND_TYPE_NOTIFICATION = 1` |

```kotlin
data class QuietRule(
    val days: Set<Int>,      // Calendar.DAY_OF_WEEK: SUNDAY=1 … SATURDAY=7
    val startHour: Int, val startMinute: Int,
    val endHour: Int,   val endMinute: Int
)
```

### 2.3 Message alerts

| Key | Type | Default | Range / notes |
|---|---|---|---|
| `message_volume_percent` | Int | `100` | clamped **5..100** (`MIN_MESSAGE_VOLUME_PERCENT = 5`) |
| `message_sound_enabled` | Boolean | `true` | `DEFAULT_MESSAGE_SOUND_ENABLED` |
| `message_sound_type` | Int | `0` | `MESSAGE_SOUND_DEFAULT = 0`, `MESSAGE_SOUND_CONTACT = 1` |

Written directly — none of these clears repeat-call history.

### 2.4 Crash-recovery / override state

| Key | Type | Default | Notes |
|---|---|---|---|
| `override_active` | Boolean | `false` | set before mutating audio, cleared on restore |
| `saved_alarm_volume` | Int | `0` | `STREAM_ALARM` value to put back |
| `saved_dnd_filter` | Int | `INTERRUPTION_FILTER_ALL` | interruption filter to put back |
| `last_perm_warning` | Long | `0` | 24 h throttle for permission warnings |

### 2.5 UI / misc

| Key | Type | Default | Notes |
|---|---|---|---|
| `theme_palette` | Int | `AppPalette.INDACO.ordinal` (0) | `INDACO, TEAL, ARGILLA, ARDESIA` |
| `theme_mode` | Int | `-1` (`NightMode.FOLLOW_SYSTEM`) | read through `NightMode.fromStored`; writes are always a valid `NightMode.storedValue` |

```kotlin
enum class AppPalette { INDACO, TEAL, ARGILLA, ARDESIA
    companion object { fun fromStoredOrdinal(v: Int) = values().getOrElse(v) { INDACO } } }
```

### 2.6 Listeners

`AlertSettingsRepository.addAlertChangeListener { … }` / `removeAlertChangeListener`
expose a single callback instead of raw `SharedPreferences` listeners. `PrefsDataSource`
registers one `OnSharedPreferenceChangeListener` and forwards only the eight **call-scoped**
keys: `service_enabled`, `mute_until_timestamp`, `quiet_rules`, `vip_contacts`,
`call_alert_mode`, `repeat_call_window_minutes`, `escalate_call_volume`, `volume_percent`
→ `CallMonitorService` resets repeat-call history and suspends the active alert if the gate
now fails.

`isMuted` lazily self-clears an expired `mute_until_timestamp`.

---

## 3. `corealert_call_attempts`

Written by `RepeatCallRepositoryImpl.save()`, read only if `boot == Settings.Global.BOOT_COUNT`.

```json
{
  "history": [
    { "key": "<64 lowercase hex = SHA-256(normalised VIP number)>",
      "start": 12345678,          // SystemClock.elapsedRealtime() of the FIRST call
      "count": 1 }                 // 1..4
  ],
  "active": { ... },               // dropped on restore (unknown outcome)
  "boot":   42,
  "elapsed": 987654321,
  "wall":    1758000000000
}
```

* `elapsed`/`wall` are compared at each gate check to detect clock changes
  (skew > 2 000 ms ⇒ reset).
* Restore validates `key ~ [a-f0-9]{64}`, `count ∈ 1..4`, `start >= 0`, caps at **1000** rows.
* **Cleared entirely** by `PrefsDataSource.put(..., callScoped = true)` and on boot mismatch.
* **Never included** in configuration exports (asserted by `RepeatCallConfigTest`).

---

## 4. `vip_message_alerts`

| Key | Type | Meaning |
|---|---|---|
| `pending_number` | String (normalised) | contact awaiting pairing |
| `pending_app` | String (`whatsapp` / `google_messages` / `telegram` / `viber`) | app awaited |
| `bindings` | JSON object | `"<app.storageId>\|<normalizedNumber>" → conversationHash` |
| `last_events` | JSON object | `"<app.storageId>\|<conversationHash>" → eventFingerprint` |
| `legacy_whatsapp_migrated` | Boolean | one-shot migration flag |

Key builders: `bindingKey(app, number)`, `eventKey(app, conversationHash)`.

Legacy source file `whatsapp_vip_alerts` is read once during migration and never written.

---

## 5. Backup/export format

`ConfigExporter` + `ConfigCrypto` (Settings → Backup).

**Exported:** contacts, quiet rules, sound settings (call volume, sound type, message
settings, message volume), repeat-call settings (mode, window, escalation).

**Not exported:** repeat-call sequences, message bindings, mute timer, saved override
state.

Envelope:

```json
{ "format": "corealert-enc-v1", "iter": 600000, "salt": "<b64 16B>", "iv": "<b64 12B>", "data": "<b64>" }
```

* Key: `PBKDF2WithHmacSHA256(password, salt, iter) → 256 bits` → AES-256-GCM.
* Import accepts `iter ∈ [100 000, 1 000 000]` only; legacy plain JSON is rejected;
  rule ranges are re-validated (`isValidRuleRanges`: hours 0–23, minutes 0–59, days 1..7).
* Import reads every field with `opt*` and ignores keys it does not know, so a file
  written by an older version loads without tripping over obsolete keys.
* File name: `corealert-backup-yyyyMMdd-HHmmss.json`, picked with
  `ActivityResultContracts.CreateDocument("application/json")`, written through
  `contentResolver.openOutputStream(uri)`.

---

## 6. Backup rules (system level)

`res/xml/backup_rules.xml` (`<full-backup-content>`) and
`res/xml/data_extraction_rules.xml` (`<cloud-backup>` + `<device-transfer>`) both exclude:

* `corealert_prefs.xml`

and the manifest sets `android:allowBackup="false"` regardless.
