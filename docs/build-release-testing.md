# Building, testing & releasing

---

## 1. Prerequisites

| Requirement | Version / notes |
|---|---|
| JDK | **21** for the Gradle daemon (`gradle/gradle-daemon-jvm.properties`, toolchainVersion=21, foojay auto-download per OS/arch); the **app** compiles to Java/Kotlin **17** |
| Gradle | 9.3.1 via wrapper (`./gradlew`) |
| Android SDK | compileSdk **37**; AGP 9.1.0 |
| Android Studio | any version supporting AGP 9 |
| NDK | not required (no native code of our own); `abiFilters` restrict output to `arm64-v8a` |
| Kotlin | 2.2.10 (external plugin; `android.builtInKotlin=false`) |

`gradle.properties` sets `org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8` and
`android.nonTransitiveRClass=false`, which lets every module's generated `R` carry the
transitive resource table (all resources live in `:core:ui` — see
[architecture.md](architecture.md#13-resources--r-classes)).

---

## 2. Build commands

```bash
# Production flavour — no secrets needed
./gradlew assembleProductionRelease
# → app/build/outputs/apk/production/release/app-production-release.apk

./gradlew assembleProductionDebug
# → app/build/outputs/apk/production/debug/app-production-debug.apk

./gradlew assembleUatRelease
# → app/build/outputs/apk/uat/release/app-uat-release.apk

# Unit tests — all seven modules, both build types
./gradlew test

# Per-module tests
./gradlew :feature:test :core:domain:test :core:policy:test :core:data:test :core:monitoring:test

# Lint (app + its dependency graph)
./gradlew lintProductionDebug

# Assemble everything
./gradlew assemble
```

There are **no instrumentation tests** (`androidTest/` does not exist), so `connectedCheck`
has nothing to run.

---

## 3. Variants

Flavor dimension **`distribution`** × build type **`debug` / `release`** → 4 variants:

| Variant | applicationId | Notes |
|---|---|---|
| `productionDebug` | `com.arjun.core_alert` | debug signing |
| `productionRelease` | `com.arjun.core_alert` | release keystore, minify **off** |
| `uatDebug` | `com.arjun.core_alert.uat` | debug signing |
| `uatRelease` | `com.arjun.core_alert.uat` | release keystore, minify off |

Key config in `app/build.gradle.kts`:

```kotlin
namespace = applicationId = "com.arjun.core_alert"
compileSdk = 37; minSdk = 29; targetSdk = 37
versionCode = 51; versionName = "2.22.0"
ndk { abiFilters.add("arm64-v8a") }              // 64-bit ARM only
dependenciesInfo { includeInApk = false; includeInBundle = false }
buildFeatures { compose = true; buildConfig = true }   // + compose plugin (Kotlin 2.2.10)
// release: isMinifyEnabled = false, isShrinkResources = false,
//          vcsInfo { include = false }   ← keeps VCS metadata out of the APK
```

`abiFilters = arm64-v8a` has no effect on our own code (we ship no `.jni` libs) but drops
32-bit and x86 ABIs from the package.

### 3.1 Flavor matrix

The **only** difference between the flavors is the application id:

| | `uat` | `production` |
|---|---|---|
| application id | `…corealert.uat` (side-by-side install) | `…corealert` |

Everything else — resources, permissions, `BuildConfig`, code paths — is identical.

---

## 4. `local.properties`

Read by `app/build.gradle.kts` if present (gitignored along with `*.jks` / `*.keystore`):

| Property | Feeds |
|---|---|
| `STORE_PASSWORD` | release signing config (keystore password) |
| `KEY_PASSWORD` | release signing config (key password) |

The Android SDK path (`sdk.dir`) is also read from `local.properties` by the Android Gradle
plugin as usual.

---

## 5. Signing

```kotlin
signingConfigs.create("release") {
    storeFile     = file("../corealert-release.jks")   // repo root, gitignored
    storePassword = localProps.getProperty("STORE_PASSWORD", "")
    keyAlias      = "corealert"
    keyPassword   = localProps.getProperty("KEY_PASSWORD", "")
}
```

* Both flavors use the same release config (they have different application ids, so they
  are separate apps on a device and can coexist).
* `debug` uses the default debug keystore.
* The keystore **must not** be committed — `.gitignore` covers `*.jks` and `*.keystore`.

---

## 6. Dependencies

All coordinates and versions live in the **Gradle version catalog**
`gradle/libs.versions.toml` (`[versions]`, `[libraries]`, `[bundles]`, `[plugins]`); the
build scripts only use `libs.*` accessors and `alias(libs.plugins.…)` — the tables below
are the effective coordinates that catalog resolves to.

### 6.1 Shared

| Dependency | Why |
|---|---|
| `androidx.core:core-ktx:1.12.0` | KTX extensions |
| `androidx.appcompat:appcompat:1.6.1` | `AppCompatActivity`, `AppCompatDelegate` (night mode) |
| `com.google.android.material:material:1.11.0` | Material 3 theme parent for the activity window (`Theme.CoreAlert.*`) |
| `androidx.compose:compose-bom:2024.09.03` | BOM pinning all Compose artifacts |
| `androidx.activity:activity-compose:1.9.3` | `setContent`, `BackHandler` |
| `androidx.compose.foundation:foundation`, `ui`, `ui-graphics`, `material3`, `runtime-saveable` | the whole UI: screens, cards, dialogs, drawer |
| `androidx.lifecycle:lifecycle-runtime-ktx:2.8.7` | lifecycle integration |
| `androidx.navigation:navigation-compose:2.8.0` | `:feature` — `NavHost`, drawer/back-stack state (`rememberNavController`) |
| `io.insert-koin:koin-androidx-compose:4.0.4` | `:feature` — `koinViewModel()` for the screen view models |
| `io.insert-koin:koin-android:4.0.4` | DI (`:app`, `:feature`, `:core:data`, `:core:monitoring`); picked because its transitive versions match our stack (activity 1.9.3, lifecycle 2.8.7, kotlin-stdlib 2.0.21) — Koin 4.2.x would force `activity-ktx 1.12.4` / `compileSdk 36` |
| `com.jakewharton.timber:timber:5.0.1` | logging facade (`:app`, `:core:monitoring`); `DebugTree` planted in `CoreAlertApp.onCreate`, every call keeps the historical tag via `Timber.tag(...)` |

Dependencies are declared per module (each one via `libs.*` accessors): `:core:ui` owns the
Compose BOM + `material`,
`:core:data` adds `appcompat` (for `AppCompatDelegate` constants in `ThemeRepositoryImpl`),
`:core:policy` depends only on `:core:domain`, `:core:monitoring` adds `core-ktx` and
depends on `:core:domain` + `:core:policy` + `:core:ui` (it talks to storage through
repository interfaces), `:feature` declares the Compose BOM + navigation + Koin-Compose
against `:core:domain` + `:core:policy` + `:core:data` + `:core:monitoring` + `:core:ui`,
`:app` adds the rest and pulls the whole graph in via `implementation(project(":feature"))`.

### 6.2 Flavor-only

None — both flavors pull exactly the same dependency set.

### 6.3 Test

| Dependency | Why |
|---|---|
| `junit:junit:4.13.2` | test runner |
| `org.json:json:20240303` | real `JSONObject` on the JVM — Android's stub implementation throws |

### 6.4 Repositories

`settings.gradle.kts` uses `RepositoriesMode.FAIL_ON_PROJECT_REPOS` with `google()` +
`mavenCentral()` in both `pluginManagement` and `dependencyResolutionManagement` — no
per-module repositories are permitted.

---

## 7. R8 / ProGuard

`app/proguard-rules.pro` exists but is **dormant** (`isMinifyEnabled = false`,
`isShrinkResources = false`). It holds a generic JNI-name keep rule. All of it is inert
while minify stays off.

If minify is ever enabled, expect to add keep rules for reflection-used classes
(`JSONObject` field names are not an issue — they're string keys).

---

## 8. Test suite

**22 test files, 117 `@Test` methods**, all JVM unit tests, each living in the module
that owns the code under test.

| Module | Files | Tests | Location |
|---|---|---|---|
| `:core:domain` | 2 | 20 | `core/domain/src/test/…` |
| `:core:policy` | 15 | 69 | `core/policy/src/test/…` |
| `:core:data` | 3 | 23 | `core/data/src/test/…` |
| `:core:monitoring` | 1 | 2 | `core/monitoring/src/test/…` |
| `:feature` | 1 | 3 | `feature/src/test/…` |

`./gradlew test` runs every module for both build types. `:app` has no unit tests.

### 8.1 The suite

`:core:domain` — `AppPaletteTest` (2) · `PhoneUtilsTest` (18)

`:core:policy` — `AlertGatePolicyTest` (5) · `AlertVolumePolicyTest` (3) ·
`AudioOverridePolicyTest` (5) ·
`ContactImportPolicyTest` (4) · `HomeWarningPolicyTest` (9) · `MutePolicyTest` (4) ·
`OverrideStatePolicyTest` (3) · `PermissionPolicyTest` (2) · `QuietHoursPolicyTest` (3) ·
`QuietRulePolicyTest` (2) · `RepeatCallTrackerTest` (14) · `ServiceEnablePolicyTest` (6) ·
`VipMessageNotificationPolicyTest` (5) · `VoipCallPolicyTest` (2) ·
`WarnThrottlePolicyTest` (2)

`:core:data` — `ConfigCryptoTest` (9) · `ConfigExporterTest` (10) · `RepeatCallConfigTest` (4)

`:core:monitoring` — `GoogleMessagesVipResolverTest` (2)

`:feature` — `AppRouteTest` (3)

Highlights:

* **`RepeatCallTrackerTest` (14)** — window anchoring/no-sliding, duplicate-broadcast
  suppression, answering resets only that caller, outgoing/call-waiting excluded,
  settings/pause/quiet resets, process-death conservatism, escalation series.
* **`ConfigCryptoTest` (9)** — round-trip, wrong password, tampered ciphertext, iteration
  bounds, randomised output, envelope detection.
* **`ConfigExporterTest` (10)** — export/import round-trip, exported JSON shape, tolerant
  import (unknown/legacy keys ignored, missing fields defaulted), quiet-rule range
  validation including zero-duration and out-of-range rules.
* **`PhoneUtilsTest` (18)** — normalisation idempotence, Italian heuristics, US numbers
  untouched, suffix-match ≥ 8 digits.
* **`AudioOverridePolicyTest` (5)** — when the override fires vs is skipped.
* **`HomeWarningPolicyTest` (9)** — the banner priority order.

### 8.2 Testing conventions

* Decision logic lives in **`:core:policy`** as pure objects, so it can be tested
  without Android (`AlertGatePolicy`, `RepeatCallPolicy`, `QuietHoursPolicy`, …).
* Tests assert **exact numeric constants** (windows, caps, iteration counts), so
  changing a policy breaks a test on purpose.
* Repository implementations are plain `SharedPreferences`/`org.json` code; the JSON parts
  are tested on the JVM through `org.json:json` (`ConfigExporterTest`, `RepeatCallConfigTest`).

---

## 9. Release checklist

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Run `./gradlew test` and `./gradlew lintProductionDebug`.
3. Build and smoke-test:
   * `./gradlew assembleProductionRelease`
   * `./gradlew assembleUatRelease` if a side-by-side build is needed
4. Verify the APK declares **no** `INTERNET` permission and pulls in no HTTP client:
   `adb shell dumpsys package com.arjun.core_alert | grep -i permission`.
5. Sign with `corealert-release.jks` (handled by the release build config).
6. Publish the signed APK (sideload / your own channel) — the app performs no self-update,
   so a new release is installed like any other package.

### Version history

| versionCode | Highlights |
|---|---|
| 1 | initial release: VIP contacts, Silent/Vibrate/DND override, volume 50–100 %, state restore, boot start, EN+IT |
| 42 | full visual redesign; four palettes (Indigo/Teal/Clay/Slate); system/light/dark; status-bar contrast |
| 47 | VIP message alerts (WhatsApp, Google Messages, Telegram); multi-number contact import |
| 49 | repeat-call mode (first/second within 3/5/10 min, per-number exceptions); optional volume doubling below 50 % |
| 51 | live map (session duration, distance, average speed, last-minute speed, path + follow mode); open the latest position in an external maps app |

---

## 10. Repository hygiene

`.gitignore` covers:

```
*.iml  .gradle  /local.properties  /.idea  /build  **/build  /captures
.externalNativeBuild  .cxx  local.properties  *.jks  *.keystore  .kotlin/
/app/src/debug/  /app/release/
.superpowers/
```

`**/build` covers every module's build output (`app/`, `feature/`, `core/*/`), so
`build/` directories are never committed regardless of how many modules are added.

Note that `/app/src/debug/` is ignored — a developer's local debug source overrides are
never committed.
