# Architecture

CoreAlert is a **seven-module** Gradle project built around one long-lived foreground
service, with **Koin** providing dependency injection. This document describes the module
layout, the DI graph, the product-flavor split, and how the runtime pieces fit together.

---

## 1. Repository layout

```
CoreAlert/
├── app/                          # :app — activity + application shell, Koin graph, flavors
│   ├── build.gradle.kts          # flavors, SDK levels, signing, dependencies
│   ├── proguard-rules.pro        # R8 rules (dormant: minify is disabled)
│   └── src/
│       └── main/                 # 4 Kotlin files, 155 LOC (MainActivity, BaseActivity, Application, appModule)
├── feature/                      # :feature — all screens, view models, navigation
│   └── src/
│       ├── main/                 # 9 Kotlin files, 3 069 LOC (home/, settings/, privacy/, navigation/)
│       └── test/                 # AppRouteTest (1 file, 25 LOC)
├── core/
│   ├── domain/                   # :core:domain — models + repository interfaces
│   │   └── src/main/             # 18 files, 300 LOC   (+ 2 test files)
│   │       └── models/           # one type per file (11 files, 94 LOC)
│   ├── policy/                   # :core:policy — pure decision policies
│   │   └── src/main/             # 15 files, 369 LOC   (+ 15 test files)
│   ├── data/                     # :core:data — repositories + persistence
│   │   └── src/main/             # 10 files, 874 LOC   (+ 3 test files)
│   ├── monitoring/               # :core:monitoring — service, receivers, listener
│   │   └── src/main/             # 8 files, 1 149 LOC  (+ 1 test file)
│   └── ui/                       # :core:ui — ALL resources, theme, shared composables
│       └── src/main/             # 5 files, 937 LOC + res/ (25 drawables, 8 value files, …)
├── build.gradle.kts              # root script — plugin aliases from the version catalog
├── settings.gradle.kts           # repos, rootProject "CoreAlert", 7 includes
├── gradle.properties             # AGP 9 compatibility flags, nonTransitiveRClass=false
├── gradle/
│   ├── libs.versions.toml        # version catalog — every plugin/dependency version + bundles
│   ├── wrapper/                  # Gradle 9.3.1
│   └── gradle-daemon-jvm.properties   # daemon toolchain = JVM 21 (foojay downloads)
├── licenses/Manrope-OFL.txt      # font licence
├── LICENSE                       # GPL-3.0-or-later
└── docs/                         # this documentation
```

There is **no** `androidTest/` source set — the project is covered by JVM unit tests only
(22 files / 117 tests, living in the module that owns the code they exercise).

### 1.1 Module dependency graph

```
:app ──► :feature ──┬──► :core:ui ──────────► :core:domain
 │   │                  ├──► :core:policy ────► :core:domain   (pure decision objects)
 │   │                  ├──► :core:data ──────► :core:domain   (repositories)
 │   │                  │        └────────────► :core:policy
 │   │                  └──► :core:monitoring ─► :core:ui, :core:policy, :core:domain
 │   │
 │   ├──────────────────────────────────► :core:monitoring   (manifest components, Koin modules)
 ├──────────────────────► :core:data ────► :core:domain      (Koin modules, repositories)
 ├──────────────────────► :core:policy                        (pure decision objects)
 ├──────────────────────► :core:ui                            (theme)
 └──────────────────────► :core:domain                        (interfaces, models)
```

| Module | Namespace | Holds | Depends on |
|---|---|---|---|
| `:core:domain` | `…corealert.domain` | `models/` — **one type per file** (`VipContact`, `QuietRule`, `AppPalette`, `BuildInfo`, `MessageApp`, `MessageAlertState`, `PendingMessagePairing`, `CallAlertMode`, `CallAlertDecision`, `NightMode`), the **repository interfaces** (`ContactRepository`, `AlertSettingsRepository`, `ThemeRepository`, `OverrideStateRepository`, `RepeatCallRepository`, `MessageBindingRepository`), `util/PhoneUtils.kt` (number normalisation / matching) | — |
| `:core:policy` | `…corealert.policy` | **pure decision objects** — call gate, repeat-call policy/tracker, quiet hours, mute, volume, override restore, contact import, warning priority, message + VoIP notification policies, audio override, permission health, service-enable gate, quiet-rule validation, warning throttle | domain |
| `:core:data` | `…corealert.data` | the repository **implementations** (`*RepositoryImpl`), the `PrefsDataSource` they share, `ConfigExporter` / `ConfigCrypto` | domain, policy |
| `:core:ui` | `…corealert.coreui` | **every** `res/` entry (drawables, values, fonts, xml, mipmaps), `CoreAlertTheme`, shared components, `ThemeManager` | domain |
| `:core:monitoring` | `…corealert.monitoring` | `CallMonitorService`, the two receivers, the notification listener, `VipMessageAlertsProvider` | domain, policy, ui |
| `:feature` | `…corealert.feature` | **every screen** — `navigation/` (`CoreAlertRoot`, `AppRoute`, `featureModule`), `home/` (`HomeScreen`, `HomeDialogs`, `HomeViewModel`), `settings/` (`SettingsScreen`, `SettingsViewModel`), `privacy/` (`PrivacyScreen`); owns the Navigation-Compose graph and obtains the view models with `koinViewModel()` | domain, policy, data, monitoring, ui |
| `:app` | `…corealert` | `MainActivity` / `BaseActivity` / `CoreAlertApp` (the `Application`), `appModule`, the manifest | `:feature` + all of the above |

`:core:monitoring → :core:ui` exists **only** because the service and listener post
notifications built from shared strings and drawables; no composable or theme type crosses
that edge.

### 1.2 The repository layer

Storage is hidden behind interfaces declared in `:core:domain` and implemented in
`:core:data` (suffix `Impl`), so no consumer ever sees a `SharedPreferences`:

| Interface (domain) | Implementation (data) | Owns |
|---|---|---|
| `ContactRepository` | `ContactRepositoryImpl` | VIP contacts (JSON list) |
| `AlertSettingsRepository` | `AlertSettingsRepositoryImpl` | service switch, mute, volumes, sound types, call mode, quiet rules, permission-warning throttle, **alert change listener** |
| `ThemeRepository` | `ThemeRepositoryImpl` | palette + `NightMode` (`setPalette`, `setNightMode`) |
| `OverrideStateRepository` | `OverrideStateRepositoryImpl` | override snapshot (`beginOverride(vol, filter)` / `endOverride()`) |
| `RepeatCallRepository` | `RepeatCallRepositoryImpl` | boot-scoped repeat-call history (per-service instance) |
| `MessageBindingRepository` | `MessageBindingRepositoryImpl` | message pairing, bindings, dedup fingerprints |

Reads are `val` properties; every write is an explicit method (`setServiceEnabled`,
`setVolumePercent`, `muteUntil`, `clearMute`, …), so a repository can never be assigned to.
Sanitising (clamping volumes, normalising enums, call-scoped invalidation) happens inside
the implementation, not at the call site.

`PrefsDataSource` (internal to `:core:data`) owns both preference files and the write
semantics: `put(key, value, callScoped = true)` clears the repeat-call attempts store
before writing and, through a single registered `OnSharedPreferenceChangeListener`,
notifies `AlertSettingsRepository.addAlertChangeListener { … }` for the eight keys that
affect an in-flight alert. Everything else is a plain read/write.

Because `:core:monitoring` only needs the interfaces, it no longer depends on `:core:data`
at all — its Koin lookups (`by inject()`) are bound in `dataModule` on the other side of
the graph.

### 1.3 Resources & R classes

All resources live in `:core:ui`, and `gradle.properties` sets
`android.nonTransitiveRClass=false` (plus `android.enableAppCompileTimeRClass=false`), so
every module's generated `R` carries the transitive resource table. Consequences:

* `:app` code keeps its existing `import com.arjun.core_alert.R` — unchanged.
* `:feature` imports `com.arjun.core_alert.feature.R`, which carries the transitive table,
  so `R.string.*` / `R.drawable.*` keep working inside the screens.
* Files inside `:core:ui` / `:core:monitoring` that need resources import their own
  module's R (`com.arjun.core_alert.coreui.R`) — a library cannot see the app's R.

---

## 2. Product flavors

One flavor dimension, `distribution`, with two flavors. Neither flavor has its own source
set any more — every build compiles `main` only; the flavor solely selects the
application id.

| | `uat` | `production` |
|---|---|---|
| application id | `com.arjun.core_alert.uat` | `com.arjun.core_alert` |
| Installable side-by-side with the production build | yes (suffix `.uat`) | — |

There is **no** other functional divergence anywhere in the codebase — no flavor-specific
code, layouts, strings, manifests or `BuildConfig` fields. Updates are always installed
from outside the app (sideload, store); nothing in the APK fetches anything from the
network.

---

## 3. Runtime components

### 3.1 Manifest components (`app/src/main/AndroidManifest.xml`)

The manifest stays in `:app` (it also carries permissions, `applicationId` placeholders
and the default theme) while the service/receiver/listener *classes* live in
`:core:monitoring` — Android resolves components by fully-qualified class name, so the
relative names below (`.MainActivity`, `.CallMonitorService`, …) keep working across
modules because every class stays in package `com.arjun.core_alert`.

| Component | Type | Notes |
|---|---|---|
| `MainActivity` | activity | LAUNCHER; single-activity Compose host — mounts `CoreAlertRoot` (NavHost + drawer); `android:configChanges="uiMode"` so night-mode changes are applied in place instead of recreating |
| `CallMonitorService` | service | **the core**; `foregroundServiceType="specialUse"` with a `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` justification string |
| `VipMessageNotificationListener` | service | `NotificationListenerService`, requires `BIND_NOTIFICATION_LISTENER_SERVICE` |
| `BootReceiver` | receiver | exported, guarded by `RECEIVE_BOOT_COMPLETED`; handles `BOOT_COMPLETED` **and** `MY_PACKAGE_REPLACED` (restart monitoring after an app update) |
| `MuteTimerReceiver` | receiver | not exported; action `com.arjun.core_alert.ACTION_UNMUTE` |

Application attributes: `CoreAlertApp` (`android:name`), `allowBackup="false"`,
`localeConfig`, default theme `Theme.CoreAlert.Indaco`. There are no `<provider>` entries
and no `INTERNET` permission — the manifest declares no network capability at all.

### 3.2 `CoreAlertApp` — process start

`CoreAlertApp.onCreate()` does exactly three things, in this order:

1. `Timber.plant(Timber.DebugTree())` — logging goes through Timber everywhere
   (`Timber.tag("CoreAlert").d(...)` keeps the historical logcat tags), and a receiver or
   service can run before anything else, so the tree has to be planted first;
2. `startKoin { androidContext(...); modules(appModule, featureModule, dataModule, monitoringModule) }` —
   the whole object graph must exist before any Activity, receiver or service runs;
3. applies the stored night mode (`ThemeManager.applyNightMode(get<ThemeRepository>().nightMode)`).

The Koin modules are declared next to the code they wire:

| Module | File | Provides |
|---|---|---|
| `dataModule` | `core/data/…/DataModule.kt` | `single PrefsDataSource`, one `single` per repository (bound to its interface), `factory<RepeatCallRepository> { (phoneBusy) -> … }` |
| `monitoringModule` | `core/monitoring/…/MonitoringModule.kt` | `single VipMessageAlertsProvider` |
| `featureModule` | `feature/…/FeatureModule.kt` | `viewModel HomeViewModel`, `viewModel SettingsViewModel { (onContactsChanged) -> … }` |
| `appModule` | `app/…/AppModule.kt` | `single BuildInfo` |

Stateless `object` policies (all of them in `:core:policy`) are **not** registered —
they have no constructor and no state; everything that needs a `Context` or holds state
goes through Koin.

### 3.3 `CallMonitorService` — the heart of the app

A sticky foreground service (670 lines) that resolves its collaborators from Koin
(`KoinComponent`): `settings` / `contactRepository` / `overrideState` via `by inject()`,
`repeatCalls` via `get { parametersOf(callIdle) }`.
Responsibilities:

- register the `TelephonyManager.ACTION_PHONE_STATE_CHANGED` receiver,
- decide, per call, whether to override the device's audio state,
- save/mutate/restore `STREAM_ALARM` volume and the DND interruption filter,
- play the override ringtone (with a fallback chain) and vibrate,
- run the **alert gate** every second so mute/quiet/service-off takes effect mid-alert,
- host the message-alert override path.

Lifecycle highlights:

| Hook | What happens |
|---|---|
| `onCreate` | resolve `repeatCalls` from Koin, snapshot telephony state **before** registering the receiver (race fix), `restoreStaleOverrideState()` self-heal, notification channels, `startForeground(SPECIAL_USE)` |
| `onStartCommand` | handles `ACTION_MESSAGE_ALERT`; returns `START_STICKY` |
| `onDestroy` | stop sound/vibrator, `restoreAudio()` if overriding, unregister everything, `instance = null` |

Full details in [call-monitoring.md](call-monitoring.md).

### 3.4 Process/thread picture

```
main thread
 ├─ MainActivity + Compose UI (HomeViewModel: 60 s mute-countdown Handler, cancelled in onCleared())
 ├─ CallMonitorService (broadcast receiver callbacks, Handler alerts/gates)
 └─ Notification listener callbacks (VipMessageNotificationListener)

background threads
 └─ ad-hoc Thread { } (backup import decrypt/parse)
```

Communication inside the process uses plain method calls, explicit `Intent`s
(`ACTION_MESSAGE_ALERT` from the listener to the service) and the repository
alert-change listener (`AlertSettingsRepository.addAlertChangeListener`).

---

## 4. Dependency overview

Shared dependencies (both flavors). Coordinates and versions are declared **once** in the
Gradle version catalog `gradle/libs.versions.toml`; each module's `build.gradle.kts` then
refers to them through `libs.*` accessors (`:app`, `:feature` and the `core/*` build files):

| Dependency | Purpose |
|---|---|
| `io.insert-koin:koin-android:4.0.4` | dependency injection (`:app`, `:feature`, `:core:data`, `:core:monitoring`) — chosen because it tracks our toolchain (activity 1.9.3, lifecycle 2.8.7) |
| `androidx.navigation:navigation-compose:2.8.0`, `io.insert-koin:koin-androidx-compose:4.0.4` | `:feature` only — `NavHost` + drawer navigation, `koinViewModel()` for the screen view models |
| `androidx.core:core-ktx`, `appcompat`, `material` + the Compose BOM (`foundation`, `ui`, `material3`, `activity-compose`) | UI plumbing (single-activity Compose; `material` is needed by `:core:ui` for the `Theme.Material3` parent) |
| `junit:4.13.2`, `org.json:json:20240303` | unit tests (real `JSONObject`, since Android stubs throw) |

See [build-release-testing.md](build-release-testing.md) for the full list and rationale.

---

## 5. Data flow: incoming VIP call

```
TelephonyManager.ACTION_PHONE_STATE_CHANGED
        │  (only broadcasts carrying EXTRA_INCOMING_NUMBER are honoured)
        ▼
CallMonitorService.phoneReceiver
        │  RINGING → contactRepository.findVipContact(number)
        │           → RepeatCallRepository.onRinging(contact) → CallAlertDecision?
        ▼
beginAudioOverride(...)                       ┐
  ├─ AudioOverridePolicy.shouldOverride(...)  │ skip when the phone would
  ├─ save STREAM_ALARM volume + DND filter    │ ring anyway
  ├─ NotificationManager.setInterruptionFilter(ALL)   (if DND access granted)
  ├─ AlertVolumePolicy.targetStreamVolume(...) → setStreamVolume(STREAM_ALARM, …)
  └─ scheduleAlertGateCheck()  (every 1 000 ms)
        ▼
startOverrideSound()  → MediaPlayer(USAGE_ALARM), candidate chain
startVibration()      → pattern (0, 500, 500) repeating
        ▼
IDLE → restoreAudio()  → volume + DND filter back to saved values
```

---

## 6. Cross-cutting design rules

These conventions show up repeatedly and are worth internalising:

1. **Pure, tiny, unit-tested policy objects.** Anything that decides *whether* or *how
   much* lives in its own file in **`:core:policy`** (`AlertGatePolicy`,
   `AlertVolumePolicy`, `AudioOverridePolicy`, `OverrideStatePolicy`, `MutePolicy`,
   `RepeatCallPolicy`, `QuietHoursPolicy`, `VoipCallPolicy`, `HomeWarningPolicy`, …)
   with a dedicated `*Test.kt` — including `PermissionPolicy`, `ServiceEnablePolicy`,
   `WarnThrottlePolicy` and `QuietRulePolicy`. Only non-decision code stays in
   `:core:domain` (repository interfaces, `models/`, `util/PhoneUtils.kt`).
2. **Conservative failure.** Unknown call outcome ⇒ not a missed call; ambiguous
   contact match ⇒ no auto-pair; huge clock gap ⇒ reset history.
3. **Minimise and restore mutated system state.** Only `STREAM_ALARM` volume and the
   DND interruption filter are ever changed; both are persisted before mutation so a
   crash can be undone on next start.
4. **Hash, don't store.** Phone numbers appear in logs only as `SHA-256(x).take(8)`;
   repeat-call keys are full SHA-256 hex; conversation ids and dedup fingerprints are
   SHA-256; notification text and contact names are never persisted.
5. **Gate at every layer.** The monitoring gate is checked when an alert triggers, every
   second while it runs, on every relevant preference change, and inside the
   notification listener.
6. **Inject, don't construct.** Anything with a dependency (repositories, the view models,
   `VipMessageAlertsProvider`) is obtained from Koin
   (`by inject()` / `get { parametersOf(...) }`); only stateless `object` policies are
   called directly.
7. **Interfaces, not storage.** Every consumer depends on a domain repository interface;
   only `:core:data` knows that the backing store is `SharedPreferences`. `:core:domain`
   stays Android-free, and `:core:monitoring` never references a class owned by `:core:data`
   or `:app` (the launcher activity is addressed by component name).
