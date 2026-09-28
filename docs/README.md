# CoreAlert — Documentation

Complete technical documentation for **CoreAlert**, a free and open-source Android app that makes
calls and messages from whitelisted VIP contacts audible even in Silent / Vibrate /
Do Not Disturb mode.

| | |
|---|---|
| Package / application id | `com.arjun.core_alert` |
| Language | Kotlin (JVM target 17) |
| UI toolkit | Jetpack Compose (Material 3), single-activity |
| minSdk / targetSdk / compileSdk | 29 (Android 10) / 37 / 37 |
| Current version | 2.22.0 (`versionCode` 51) |
| Build system | Gradle 9.3.1 + AGP 9.1.0 + Kotlin 2.2.10 |
| Dependency management | Gradle version catalog — `gradle/libs.versions.toml` (versions, libraries, bundles, plugin aliases) |
| Modules | 7 — `:app`, `:feature`, `:core:domain`, `:core:policy`, `:core:data`, `:core:monitoring`, `:core:ui` |
| Dependency injection | Koin 4.0.4 (`startKoin` in `CoreAlertApp.onCreate`) |
| Logging | Timber 5.0.1 (`DebugTree` planted in `CoreAlertApp.onCreate`, tags preserved via `Timber.tag(...)`) |
| License | GPL-3.0-or-later |

## Contents

| Document | What it covers |
|---|---|
| [architecture.md](architecture.md) | Module layout & dependency graph, dependency injection, source sets, product flavors, component map, runtime processes |
| [call-monitoring.md](call-monitoring.md) | The core feature: foreground service, sound override engine (cellular + messenger/VoIP calls), all decision policies, repeat-call mode, quiet hours, mute timer |
| [message-alerts.md](message-alerts.md) | VIP message alerts for WhatsApp / Google Messages / Telegram / Viber: notification listener, hashing, pairing, dedup |
| [security-and-privacy.md](security-and-privacy.md) | Permissions, cryptography reference, threat model, data handling, what never leaves the device |
| [persistence.md](persistence.md) | Every SharedPreferences file and key, the configuration export format, Android backup rules |
| [ui-and-resources.md](ui-and-resources.md) | Screens, navigation, Compose components, theming/palettes, strings and localization, drawables |
| [build-release-testing.md](build-release-testing.md) | Building, signing, flavors, dependencies, the test suite, release checklist |
| [class-reference.md](class-reference.md) | Index of every Kotlin source file, grouped by module and responsibility |

## Reading order

New to the codebase? Read in this order:

1. **architecture.md** — understand the seven modules, the Koin graph and the two flavors.
2. **call-monitoring.md** — the app exists for this; everything else is optional.
3. **persistence.md** — most behaviour is driven by the repository-owned preference keys.
4. **security-and-privacy.md** — before changing anything that touches permissions or crypto.
5. The rest as needed.

## Quick start

```bash
# Production flavor (the shipping build)
./gradlew assembleProductionRelease
# → app/build/outputs/apk/production/release/app-production-release.apk

# Uat flavor (side-by-side install, application id suffix ".uat")
./gradlew assembleUatRelease

# Unit tests
./gradlew test
```

See [build-release-testing.md](build-release-testing.md) for prerequisites, signing and
the full variant matrix.

## Conventions used in this documentation

- File paths are relative to the repository root unless stated otherwise.
- Kotlin paths are prefixed with their module: `monitoring/CallMonitorService.kt` →
  `core/monitoring/src/main/java/com/arjun/core_alert/CallMonitorService.kt`,
  `data/AlertSettingsRepositoryImpl.kt` → `core/data/src/main/...`, `domain/util/PhoneUtils.kt` →
  `core/domain/src/main/...`, `policy/AlertGatePolicy.kt` → `core/policy/src/main/...`.
  `feature/home/HomeScreen.kt` → `feature/src/main/java/com/arjun/core_alert/feature/home/HomeScreen.kt`,
  `navigation/CoreAlertRoot.kt` → `feature/src/main/java/com/arjun/core_alert/feature/navigation/CoreAlertRoot.kt`.
  Files that belong to `:app` keep their real sub-path
  (`MainActivity.kt` → `app/src/main/java/com/arjun/core_alert/MainActivity.kt`,
  `CoreAlertApp.kt` → `app/src/main/...`); a named source set replaces `main`
  when a file does not live there.
- Numbers (thresholds, timeouts, caps) quoted here are the values found in the source
  at `versionCode` 51 / `2.22.0`; always treat the code as authoritative.

## Credits

CoreAlert is a continuation of **[SOS Ring](https://github.com/JackRushante/SOSRing)** by
**JackRushante (Lorenzo Marci)** — thank you for the original app (GPL-3.0-or-later).

The Compose migration, the CoreAlert rebrand and the new features (Viber message alerts,
in-app English/Burmese language switcher, live appearance switching, the seven-module
structure and this documentation) were added by **[Arjun](https://github.com/newarjun101)**
([repository](https://github.com/newarjun101/core-alert)).

Contact: **newarjun101@gmail.com** · **+95 9 978770588** — for any help, or if you need any
other app, I am always here to assist.
