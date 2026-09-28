# CoreAlert

**Make calls and messages from VIP contacts audible even in Silent / Vibrate / Do Not Disturb mode.**

CoreAlert is a FOSS Android app that overrides Silent, Vibrate, and Do Not Disturb when you receive a call or supported direct message from a whitelisted VIP contact. After the alert ends, your phone is restored to its exact previous state.

CoreAlert is based on **[SOS Ring](https://github.com/JackRushante/SOSRing)** by **JackRushante (Lorenzo Marci)** — thank you, Lorenzo, for the original app.

## Why?

Android 14+ removed per-contact DND exceptions, and there is no built-in way to let specific contacts bypass silent mode. Google restricts `READ_CALL_LOG` on the Play Store, so this kind of app cannot be published there.

CoreAlert solves this with a foreground service that monitors incoming calls and, when a VIP contact calls, plays the ringtone via the ALARM audio stream (the one stream that bypasses DND on every device).

## Features

- Pick VIP contacts from the phonebook or enter them manually; select any or all numbers when a contact has more than one
- Audible alerts for direct WhatsApp, Google Messages (SMS/RCS), Telegram, and Viber conversations from paired VIP contacts
- Separate controls for calls (25-100%) and messages (on/off, 5-100%, Android default sound or the sound assigned to the VIP contact)
- Choose to sound from the first call or only from the second call within 3, 5, or 10 minutes, with exceptions for individual VIP numbers
- Optional volume doubling on subsequent calls when the configured call volume is below 50%, up to 100%
- Works in **Silent**, **Vibrate**, AND **Do Not Disturb** mode
- Full state restore after each alert (ringer mode, all volumes including alarm, DND)
- Temporary mute timer (1-12 hours) and Quiet Hours apply to both calls and messages
- Survives reboots (auto-start)
- Minimal battery usage (event-driven, no polling)
- Dark mode (follows system), English and Burmese (in-app language switcher)
- No ads, no analytics, no tracking, no Firebase, no third-party scripts — **100% offline**

## Repeated VIP calls

Open **Settings → When to sound for VIP calls**, or tap the call-mode summary on Home. The default remains **From the first call**; the alternative starts overriding the phone's sound settings from the **second call from the same VIP number** within 3, 5 (default), or 10 minutes. The first call keeps the phone's normal sound settings. Each VIP's menu can inherit the general rule or override it.

Only separate calls from the same VIP number count, whether they come from the phone network or from a messenger (Viber, WhatsApp, Telegram); messages never count. The window starts with the first call and does not slide. An unanswered or rejected call counts; answering resets that number's sequence. Pausing monitoring, Quiet Hours, disabling monitoring, or changing call settings resets pending sequences. Overlapping calls/call waiting do not create additional attempts.

**Double the volume on subsequent calls** is optional and off by default. It applies only when the configured base call volume is below 50%. For example, a 25% base becomes **25% → 50% → 100%**, starting with the first call allowed to sound (the second actual call in second-call mode). This changes volume between calls, not gradually during one call. The original alarm volume is restored after each call. At a base of 50% or higher, the existing volume behavior is unchanged.

Messages retain their separate settings and never advance the call counter. Their sound override is suppressed while a cellular call is ringing or ongoing.

Pending call sequences are stored locally as hashed VIP-number identifiers, monotonic start times and capped counts. They are only valid for the selected window, are not included in configuration exports, and do not survive reboot. If the app restarts during a call whose outcome is unknown, that sequence is discarded conservatively.

## Permissions

| Permission | Why |
|---|---|
| `READ_PHONE_STATE` | Detect incoming calls |
| `READ_CALL_LOG` | Get the caller's number |
| `READ_CONTACTS` | Pick VIP contacts from the phonebook |
| Notification access | Identify paired direct conversations from WhatsApp, Google Messages, Telegram, and Viber, and detect incoming messenger (VoIP) calls; message text is processed transiently and never stored |
| `ACCESS_NOTIFICATION_POLICY` | Override Do Not Disturb |
| `MODIFY_AUDIO_SETTINGS` | Change ringer mode and volume |
| `FOREGROUND_SERVICE` (`SPECIAL_USE`) | Keep the monitoring service alive |
| `VIBRATE` | Force vibration during VIP calls |
| `POST_NOTIFICATIONS` | Persistent service notification (Android 13+) |

VIP message alerts are optional. Conversation identifiers are stored only as hashes, together with technical deduplication fingerprints. CoreAlert does not store notification message text, contact names, or phone numbers extracted from notifications.

## Documentation

Full technical documentation lives in [`docs/`](docs/README.md):

| | |
|---|---|
| [Architecture](docs/architecture.md) | the seven Gradle modules, dependency injection (Koin), source sets, product flavors, component map, data flows |
| [Call monitoring](docs/call-monitoring.md) | foreground service, sound override, all decision policies, repeat-call mode, quiet hours, mute timer |
| [Message alerts](docs/message-alerts.md) | WhatsApp / Google Messages / Telegram / Viber notification pipeline, plus messenger-call detection |
| [Security & privacy](docs/security-and-privacy.md) | permissions, cryptography, threat model, data handling |
| [Persistence](docs/persistence.md) | every preference key, the configuration export format, backups |
| [UI & resources](docs/ui-and-resources.md) | screens, theming, palettes, localisation |
| [Build, test & release](docs/build-release-testing.md) | flavors, signing, dependencies, test suite, release checklist |
| [Class reference](docs/class-reference.md) | index of the Kotlin source files, grouped by module |

## Building

```bash
./gradlew assembleProductionRelease
```

The APK is produced at `app/build/outputs/apk/production/release/app-production-release.apk`.

The project is a seven-module Gradle build (`:app`, `:feature`, `:core:domain`,
`:core:policy`, `:core:data`, `:core:monitoring`, `:core:ui`) wired together with Koin; run
`./gradlew test` to execute the unit tests of every module. Plugin and dependency versions
are centralized in the version catalog `gradle/libs.versions.toml`.

## Credits & contact

### Original project — SOS Ring

CoreAlert is a continuation of **[SOS Ring](https://github.com/JackRushante/SOSRing)** by
**JackRushante (Lorenzo Marci)** — thank you, Lorenzo, for the original app: the foreground
call-monitoring service, the DND override through the ALARM audio stream and the full state
restore after every alert all come from your work. The original project is GPL-3.0-or-later,
which this one keeps.

### Compose migration & new features — Arjun

Maintained and extended by **[Arjun](https://github.com/newarjun101)**, who rebranded the app
to **CoreAlert**, migrated the original XML/Views UI to **Jetpack Compose** (Material 3,
single-activity, version catalog), split the single `app` module into **seven Gradle modules**
(`:app`, `:feature`, `:core:domain`, `:core:policy`, `:core:data`, `:core:monitoring`,
`:core:ui`), and added new features on top of the original — among them **Viber** message
alerts, the **in-app language switcher (English / Burmese)**, appearance settings (color
palette and light/dark) that switch live without recreating the app, and the per-feature
documentation in [`docs/`](docs/README.md).

- Developer: [github.com/newarjun101](https://github.com/newarjun101)
- Repository: [github.com/newarjun101/core-alert](https://github.com/newarjun101/core-alert)

### Need help?

**Contact me for any help, or if you need any other app — I am always here to assist.**

| | |
|---|---|
| GitHub | [github.com/newarjun101](https://github.com/newarjun101) |
| Repository | [github.com/newarjun101/core-alert](https://github.com/newarjun101/core-alert) |
| Email | newarjun101@gmail.com |
| Phone / WhatsApp | +95 9 978770588 |

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
