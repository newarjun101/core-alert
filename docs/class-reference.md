# Class reference

An index of every Kotlin source file, grouped by module (and, inside `:app`, by source
set). Paths are relative to the module's source root, e.g.
`core/data/src/main/java/com/arjun/core_alert/` for
`data/AlertSettingsRepositoryImpl.kt`.

Legend: **P** = pure/unit-tested policy · **UI** = Android UI · **S** = service/receiver ·
**D** = data/storage · **C** = crypto · **DI** = Koin module.

---

## 1. `:app` — composition root (main 4 files / 155 LOC)

### 1.1 Application & activity shell (`app/src/main/…`)

| File | Kind | Description |
|---|---|---|
| `CoreAlertApp.kt` | DI | `Application`: plants `Timber.DebugTree()`, `startKoin { androidContext(...); modules(appModule, featureModule, dataModule, monitoringModule) }`, then applies the stored night mode; `attachBaseContext` applies the stored app language via `LanguageManager.wrap(...)` (25 LOC) |
| `AppModule.kt` | DI | `appModule`: `single BuildInfo` — the view models moved to `featureModule` (8 LOC) |
| `MainActivity.kt` | UI | Compose host: resolves `AlertSettingsRepository` from Koin, holds `HomeViewModel` with `by viewModel()` for the lifecycle callbacks, mounts `setContent { CoreAlertTheme(ThemeManager.livePalette) { CoreAlertRoot() } }` — `livePalette` is Compose state, so palette changes recompose the theme without recreating the activity (navigation state lives in `:feature`); `onResume()`/`onPause()` forward to `HomeViewModel` and self-heal the monitoring service; `onConfigurationChanged()` (config dispatch from `BaseActivity`) calls `home.refreshStrings()` so the `Application`-resolved `callModeSummary` / `muteCountdown` strings refresh in place on a language switch without waiting for the next resume (63 LOC) |
| `BaseActivity.kt` | UI | applies the palette theme + `ThemeManager.applyPalette` before `super.onCreate`, enables edge-to-edge, system-bar colour/icon management; theme comes from the injected `ThemeRepository`; `attachBaseContext` applies the stored app language via `LanguageManager.wrap(...)`; `onConfigurationChanged` re-applies the stored language (`LanguageManager.reapply`), repaints the system bars and dispatches the new `Configuration` to the view tree so Compose refreshes in place (59 LOC) |

---

## 2. `:feature` — screens, view models & navigation (main 29 files / 3 713 LOC · test 1 / 25)

Every screen lives here, behind the single `:app` → `:feature` edge. Package
`com.arjun.core_alert.feature.*`; resources resolve through
`com.arjun.core_alert.feature.R`. No composable receives a view model: `CoreAlertRoot`
collects the view models' `StateFlow`s and passes an immutable `UiState` plus an
action lambda (`HomeScreen(state, onAction)` / `SettingsScreen(state, onAction)`). Each
screen is a family of files rather than one big one: `<Screen>Screen.kt` (shell +
dialogs union), `<Screen>UiState.kt` (the state data class), `<Screen>Action.kt` (the
sealed action contract) plus a `component/` folder per screen (`settings/component/`,
`home/component/`) holding the individual cards and dialogs.

### 2.1 Navigation (`feature/src/main/…/navigation`)

| File | Kind | Description |
|---|---|---|
| `CoreAlertRoot.kt` | UI | root composable: obtains `HomeViewModel` / `SettingsViewModel` with `koinViewModel()` (`parametersOf({ home.reloadContacts() })` for the latter), collects `home.state` / `settings.state` with `collectAsState()` and hands `state` + `onAction` to the screens, `ModalNavigationDrawer` + `Scaffold` + `TopAppBar` + `NavHost` (routes `home` / `settings` / `privacy`), drawer ⇄ back-stack sync (`popUpTo(start) { saveState }`, `launchSingleTop`, `restoreState`), Home-only FAB (dispatches `HomeAction.OpenAddChoice`), a `LaunchedEffect(current)` that dispatches `HomeAction.Refresh` whenever the `HOME` route becomes current (so the summary / permissions / contacts picked up in Settings or the system settings are re-derived on return), drawer-open `BackHandler`, drawer header/items (237 LOC) |
| `AppRoute.kt` | UI | `enum AppRoute(route, labelRes, iconRes)` — `HOME` / `SETTINGS` / `PRIVACY` + `AppRoute.fromRoute(String?)` fallback to `HOME` (19 LOC) |
| `FeatureModule.kt` | DI | `featureModule`: `viewModel HomeViewModel`, `viewModel { (onContactsChanged) -> SettingsViewModel }` (14 LOC) |

### 2.2 Home (`feature/src/main/…/home`)

| File | Kind | Description |
|---|---|---|
| `HomeScreen.kt` | UI | `HomeDialog` sealed interface (`AddChoice`, `ContactForm`, `ContactNumbers`, `RemoveContact`, `ContactCallMode`, `CallModeSettings`, `MuteHours`, `DndExplain`, `MessageAccess`, `MessagePair`) and the `LazyColumn` shell (`HomeScreen(state: HomeUiState, onAction: (HomeAction) -> Unit)`): warning banner, hero card, call-mode summary, mute, permissions, contact rows, empty state, then the dialog dispatch — every card and dialog itself lives in `component/` (83 LOC) |
| `HomeUiState.kt` | UI | `data HomeUiState` — the state contract the Home screen reads (contacts, service/permission state, warning, mute countdown, call-mode summary + the call-alert slice, `messageStates`, `dialog`) plus the `runtimePermissions` list and the `Resources`-based label helpers `messageKey` / `phoneOptionLabel` / `messageAppName` / `messageMenuTitle` (66 LOC) |
| `HomeAction.kt` | UI | `sealed interface HomeAction` — the 24 actions the Home screen dispatches (`Refresh` from navigation, service toggle, permission results, dialogs, contact CRUD, call-alert setters, message pairing, `CloseDialog`) — always routed through the `onAction` screen parameter (34 LOC) |
| `HomeViewModel.kt` | UI | `HomeViewModel` (`androidx.lifecycle.ViewModel`, `viewModel { … }` in Koin): owns a single `MutableStateFlow<HomeUiState>` — `state: StateFlow<HomeUiState>` is the only thing the UI reads and `handleAction(HomeAction)` is the only thing it calls; `setState { … }` runs `derive()` (call-mode summary, call-alert slice, per-contact message states) on every write; service/pause toggling, contact CRUD + message-app pairing, permission results; takes `Application` + `AlertSettingsRepository` + `ContactRepository` + `VipMessageAlertsProvider`; 60 s mute-countdown `Handler` cancelled in `onCleared()`; `refreshStrings()` re-derives `callModeSummary` + `muteCountdown` (called from `onResume()` and `MainActivity.onConfigurationChanged()`); `onResume()`/`onPause()`/`reloadContacts()` stay public for the activity and the settings model; `HomeAction.Refresh` runs the same `onResume()` resync (dispatched by `CoreAlertRoot` when the `HOME` route becomes current) (467 LOC) |
| `component/WarningBanner.kt` | UI | warning banner (`internal fun WarningBanner`): icon, message and an action button that fires the battery-optimisation / app-details intent; helper `TextButtonTone` stays file-private (106 LOC) |
| `component/HeroCard.kt` | UI | hero monitoring card (`internal fun HeroCard`): gradient, `HeroBell`, `HeroChip`, hint + service switch → `ServiceToggled`, and the animated `DrawScope.drawPulseRing` (only while monitoring); helpers stay file-private (224 LOC) |
| `component/CallModeSummaryButton.kt` | UI | call-mode summary pill (`internal fun CallModeSummaryButton`) — a plain `summary` + `onClick`, wired by the shell to `OpenCallModeSettings` (32 LOC) |
| `component/MuteCard.kt` | UI | mute card (`internal fun MuteCard`): countdown text + pause toggle wired to `ToggleMute` (92 LOC) |
| `component/PermissionsCard.kt` | UI | permission rows (`internal fun PermissionsCard`): registers the `RequestMultiplePermissions` launcher (`PermissionResult`) and fires the notification-policy / listener / battery intents from `LocalContext`; helper `PermissionRow` stays file-private (131 LOC) |
| `component/ContactRow.kt` | UI | contact row (`internal fun ContactRow`): initials avatar, number badge and the per-contact `ContactMenu` (message-app pairing, call mode, edit, remove); helpers `ContactMenu` / `contactInitials` stay file-private (180 LOC) |
| `component/EmptyContactsCard.kt` | UI | empty state (`internal fun EmptyContactsCard`): icon, title, body and hint pill — static, no action (74 LOC) |
| `component/HomeDialogs.kt` | UI | dispatches on `HomeUiState.dialog`: add-choice (registers the `PickContact` launcher → `ContactPicked`), add/edit contact, multi-number picker, remove / call-mode confirms, mute-hours stepper, DND and message dialogs, call-mode settings; also holds the shared `CallAlertSettingsContent` (driven by explicit `callAlertMode` / `repeatWindowMinutes` / `escalateCallVolume` / `volumePercent` params + three setters and shared with the Settings volume card) — all `SosDialog`s shaped `(…, onAction)` (390 LOC) |

### 2.3 Settings & privacy (`feature/src/main/…/settings`, `…/privacy`)

| File | Kind | Description |
|---|---|---|
| `SettingsScreen.kt` | UI | `SettingsDialog` sealed interface (`ExportPassword`, `ImportConfirm`, `ImportPassword`, `AddQuietRule`, `DeleteQuietRule`), the shell `SettingsScreen(state: SettingsUiState, onAction: (SettingsAction) -> Unit)` that stacks the cards and owns the `CreateDocument` / `OpenDocument` launchers (the suggested `corealert-backup-<timestamp>.json` name is built here), plus `exportFileName()` — the cards and dialogs themselves live in `component/` (82 LOC) |
| `SettingsUiState.kt` | UI | `data SettingsUiState` — the state contract the Settings screen reads (volumes, sound types, palette/night mode/language, quiet rules, call-alert slice, `dialog`) plus the `Resources`-based helpers `formatRuleDays` / `formatRuleTime` (61 LOC) |
| `SettingsAction.kt` | UI | `sealed interface SettingsAction` — the 22 actions the Settings screen dispatches (appearance, volumes, sound types, call-alert setters, export/import, quiet-rule CRUD, `CloseDialog`) (34 LOC) |
| `SettingsViewModel.kt` | UI | `SettingsViewModel`: owns a single `MutableStateFlow<SettingsUiState>` (`state` + `handleAction`), the export/import file work (`ConfirmExportPassword` stores the password, `ExportUri` writes the encrypted envelope through `ContentResolver`, `ImportUri` reads/validates it, `DecryptPendingImport` runs the PBKDF2 decrypt off the UI thread and posts back to the main looper, `applyImportedConfig`), quiet-rule CRUD (validation keeps the dialog open), `SelectPalette` (pref + `ThemeManager.applyPalette`), `SelectLanguage` (pref + live resources through `LanguageManager.apply`, carrying the `Activity` in the action), `SelectNightMode`, message-alert setters; takes `Application` + `AlertSettingsRepository` + `ThemeRepository` + `ContactRepository` + `onContactsChanged` + `VipMessageAlertsProvider` (288 LOC) |
| `PrivacyScreen.kt` | UI | privacy/licensing shell (`PrivacyScreen()`): a `Column` of three `PrivacyCard`s with the string resources resolved here — static, so no `UiState` / `Action` / view model (46 LOC) |
| `component/PrivacyCard.kt` | UI | privacy card (`internal fun PrivacyCard`): `CardHeader` + `OkChip` + `linkedBody()`, which turns URLs, e-mail addresses and phone numbers in the body text into tappable `LinkAnnotation.Url` links (accent + underline, opened through `LocalUriHandler`); helpers `OkChip` / `linkedBody` / `linkPattern` stay file-private (103 LOC) |
| `component/AppearanceCard.kt` | UI | appearance card (`internal fun AppearanceCard`): four `SwatchEntry` swatches → `SelectPalette`, `ModeToggle` → `SelectNightMode`, `LanguageToggle` → `SelectLanguage(language, activity)`; helpers `SwatchEntry` / `ModeToggle` / `LanguageToggle` stay file-private (221 LOC) |
| `component/MessageAlertsCard.kt` | UI | VIP-message sound card: enable switch, volume slider, default-vs-contact radio group, alpha-dimmed when disabled (87 LOC) |
| `component/VolumeCard.kt` | UI | ringtone volume slider + shared `CallAlertSettingsContent` (first/second call, repeat window, escalation switch) wired to `SetVolume` / `SetCallAlertMode` / `SetRepeatWindow` / `SetEscalateVolume` (52 LOC) |
| `component/SettingsControls.kt` | UI | the two controls shared by several cards: `ValuePill` (percent badge) and `VolumeSlider` (accent thumb/track, `steps`, `enabled`) (58 LOC) |
| `component/SoundTypeCard.kt` | UI | override-sound card: ringtone vs notification radio group → `SetOverrideSoundType` (45 LOC) |
| `component/QuietHoursCard.kt` | UI | quiet-hours card: `QuietRuleRow`s (day/time summary + delete) and the add button → `OpenAddQuietRule` (disabled past `MAX_QUIET_RULES`); helpers `QuietRuleRow` / `TextButtonLike` stay file-private (146 LOC) |
| `component/BackupCard.kt` | UI | backup card: export → `OpenExportPassword`, import → `OpenImportConfirm` (75 LOC) |
| `component/SettingsDialogs.kt` | UI | dispatches on `SettingsUiState.dialog`: `ExportPasswordDialog` (empty/mismatch validation keeps it open, otherwise `ConfirmExportPassword` + `onExport` launches the picker), import-confirm/password (⇒ `DecryptPendingImport`), `AddQuietRuleDialog` (weekday `DayChip`s, `TimeStepper`s, cross-midnight hint, `ConfirmAddQuietRule`), delete-rule confirm — all `SosDialog`s shaped `(…, onAction)` (266 LOC) |

### 2.4 Tests

| File | Description |
|---|---|
| `AppRouteTest.kt` (25 LOC) | route resolution, `fromRoute` fallback, drawer-entry order |

---

## 3. `:core:domain` — models, interfaces & helpers (18 files, 300 LOC)

No `android.*` / `androidx.*` imports anywhere in this module; it is `models/` (one type
per file), **repository interfaces** and one pure utility (`util/PhoneUtils.kt`) — it owns
every seam the other modules implement. All decision logic lives in `:core:policy` (section 4).

### 3.1 Repository interfaces (implemented in `:core:data`)

| File | Kind | Description |
|---|---|---|
| `ContactRepository.kt` | D | `getContacts` / `saveContacts` (+ default `getVipNumbers`, `findVipContact`) |
| `AlertSettingsRepository.kt` | D | `val` reads + explicit setters (`setServiceEnabled`, `setVolumePercent`, `setCallAlertMode`, `setRepeatCallWindowMinutes`, `setEscalateCallVolume`, `muteUntil`/`clearMute`, `setOverrideSoundType`, `setMessageVolumePercent`/`…SoundEnabled`/`…SoundType`, `setLastPermissionWarningMs`), quiet rules, `isMuted`/`isInQuietPeriod`, `add/removeAlertChangeListener`; owns the numeric limits (`MIN_VOLUME_PERCENT`, `MAX_QUIET_RULES`, `MESSAGE_SOUND_CONTACT`, …) (82 LOC) |
| `ThemeRepository.kt` | D | `val palette` / `val nightMode` + `setPalette`, `setNightMode` (types in `models/`) (15 LOC) |
| `OverrideStateRepository.kt` | D | read-only snapshot (`isActive`, `savedAlarmVolume`, `savedDndFilter`) mutated only via `beginOverride(volume, dndFilter)` / `endOverride()` (14 LOC) |
| `RepeatCallRepository.kt` | D | `busy`, `onRinging` → `CallAlertDecision?`, `onAnswered`, `onIdle`, `reset` |
| `MessageBindingRepository.kt` | D | pairing, bindings, dedup, unpair/migrate for the notification listener |

### 3.2 Models, seams & helpers

| File | Kind | Description |
|---|---|---|
| `models/` (11 files, 91 LOC) | D | **one model per file**, package `com.arjun.core_alert.models`: `VipContact`, `QuietRule`, `AppPalette` (injected `BuildInfo` lives beside them), `MessageApp`, `MessageAlertState`, `PendingMessagePairing`, `CallAlertMode { INHERIT, FIRST, SECOND }`, `CallAlertDecision { shouldRing, volumePercent, exactVolume }`, `NightMode { FOLLOW_SYSTEM, LIGHT, DARK }`, `AppLanguage { SYSTEM, ENGLISH, MYANMAR }` |
| `util/PhoneUtils.kt` | P | normalisation + matching (Italian heuristics, ≥8-digit suffix match) (30 LOC) |

---

## 4. `:core:policy` — pure decision policies (15 files, 369 LOC)

Everything that decides *whether* an alert may fire and *how loud* it should be. The
module depends only on `:core:domain` (models + `CallAlertMode` / `CallAlertDecision`)
and is Android-free apart from `AudioOverridePolicy`, which reads `android.media` /
`android.app` constants. Every file ships with its own `*Test.kt` in
`core/policy/src/test/…`.

| File | Kind | Description |
|---|---|---|
| `AlertGatePolicy.kt` | P | `allowsCall(monitoring, paused, quiet)`; `allowsMessage(...)` adds `messageSoundEnabled` (16 LOC) |
| `AlertVolumePolicy.kt` | P | `targetStreamVolume(max, current, percent, preserveHigher)`; percent coerced 1–100, min 1 (13 LOC) |
| `OverrideStatePolicy.kt` | P | `shouldRestoreOnStart(persisted, callIdle)` (7 LOC) |
| `MutePolicy.kt` | P | `isMuted(until, now)` — strictly `now < until` (8 LOC) |
| `HomeWarningPolicy.kt` | P | `decide(...)` picks the single Home banner: none / permissions revoked / auto-revoke / battery unrestricted (+ `enum HomeWarning`) (20 LOC) |
| `RepeatCallPolicy.kt` | P | `RepeatCallPolicy.decide` (first/second call, escalation) + `RepeatCallTracker` aggregate-session state machine (118 LOC) |
| `QuietHoursPolicy.kt` | P | `isQuiet`, `wasQuietBetween` (conservative on >10 min gaps) (40 LOC) |
| `ContactImportPolicy.kt` | P | `uniqueOptions` / `createVipContacts` / `displayName` for multi-number contact import (+ `ContactPhoneOption`) (41 LOC) |
| `VipMessageNotificationPolicy.kt` | P | supported-package map, `shouldInspect`, `conversationHash`, `eventFingerprint` (41 LOC) |
| `VoipCallPolicy.kt` | P | `isCallNotification(app, category, fullScreenIntent)`, `shouldRing(numbers, matchedVip)` (13 LOC) |
| `AudioOverridePolicy.kt` | P | `shouldOverride(ringer, volume, filter)` — only when the ringer isn't already audible (12 LOC) |
| `PermissionPolicy.kt` | P | `criticalMissing(callLog, phoneState, dnd)` → list of missing critical permissions (11 LOC) |
| `ServiceEnablePolicy.kt` | P | `canEnable(phone, callLog, dnd, notif)` — all four (7 LOC) |
| `WarnThrottlePolicy.kt` | P | at most one warning every 24 h (`INTERVAL_MS`) (6 LOC) |
| `QuietRulePolicy.kt` | P | rule validity (≥1 day, start ≠ end) (16 LOC) |

---

## 5. `:core:data` — repositories & persistence (10 files, 874 LOC)

| File | Kind | Description |
|---|---|---|
| `PrefsDataSource.kt` | D | internal owner of both preference files: typed `put(key, value, callScoped)`, call-scoped write semantics (clears repeat-call attempts), the single change listener + `add/removeAlertChangeListener` (60 LOC) |
| `ContactRepositoryImpl.kt` | D | VIP contacts JSON encode/decode (45 LOC) |
| `AlertSettingsRepositoryImpl.kt` | D | **all alert settings** (177 LOC): service, volume, call mode, repeat window, escalation, mute (+ expiry reset), sound types, message-alert settings, quiet rules, quiet-period evaluation, permission-warning throttle |
| `ThemeRepositoryImpl.kt` | D | palette + `NightMode` ordinals (24 LOC) |
| `OverrideStateRepositoryImpl.kt` | D | override snapshot; `beginOverride` writes volume + DND filter + active flag, `endOverride` clears the flag (31 LOC) |
| `RepeatCallRepositoryImpl.kt` | D | persists `RepeatCallTracker` history and gates each decision on the current settings (75 LOC) |
| `MessageBindingRepositoryImpl.kt` | D | pairing state, bindings, dedup fingerprints, number migration + legacy migration (189 LOC) |
| `ConfigExporter.kt` | D | `AppConfig` + build/import/validate of the configuration JSON, rule range validation (181 LOC) |
| `ConfigCrypto.kt` | C | PBKDF2-HmacSHA256 600 k + AES-256-GCM backup envelope `corealert-enc-v1` (75 LOC) |
| `DataModule.kt` | DI | `dataModule`: `single PrefsDataSource` + one `single` per repository bound to its interface, `factory<RepeatCallRepository>` (17 LOC) |

---

## 6. `:core:monitoring` — service layer (8 files, 1 149 LOC)

| File | Kind | Description |
|---|---|---|
| `CallMonitorService.kt` | S | **the core foreground service**: phone-state receiver, audio override save/mutate/restore, ringtone candidate chain, vibration, 1 s alert gate, message-alert trigger, VoIP call entry (`voipCallIncoming`/`voipCallEnded` + 60 s timeout), permission-warning notification (670 LOC) |
| `BootReceiver.kt` | S | restarts the service on `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` if it was enabled, re-arms the mute alarm (76 LOC) |
| `MuteTimerReceiver.kt` | S | calls `settings.clearMute()` when the alarm fires (24 LOC) |
| `VipMessageNotificationListener.kt` | S | `NotificationListenerService`; inspect → hash → pair/bind → dedup → alert, plus incoming-call detection (`category == "call"` / full-screen intent) → `voipCallIncoming`, removal → `voipCallEnded` (203 LOC) |
| `VipMessageAlertsProvider.kt` | — | injectable façade over `MessageBindingRepository`: `supported`, `hasNotificationAccess`, state/begin/cancel/unpair (39 LOC) |
| `GoogleMessagesVipResolver.kt` | — | sender URI extraction + exactly-one-VIP auto-pair resolution (85 LOC) |
| `ContactRingtoneHelper.kt` | — | per-contact `CUSTOM_RINGTONE` lookup, fails soft without `READ_CONTACTS` (45 LOC) |
| `MonitoringModule.kt` | DI | `monitoringModule`: `single VipMessageAlertsProvider` (7 LOC) |

---

## 7. `:core:ui` — design system & resources (5 files, 937 LOC + all of `res/`)

| File | Kind | Description |
|---|---|---|
| `ui/theme/CoreAlertTheme.kt` | UI | `CoreAlertTheme(palette, content)`: Manrope `FontFamily`, typography, shapes, `ColorScheme` from palette resources, `LocalCoreAlertColors` (297 LOC) |
| `ui/component/Common.kt` | UI | `AppCard`, `CardHeader`, `Overline`, `IconBadge`, `RadioRow`, `SoftPill`, `StatusText`, … (216 LOC) |
| `ui/component/Dialogs.kt` | UI | `SosDialog`, `ListDialog`, `SingleChoiceDialog`, `MultiChoiceDialog`, `SosTextField`, `TimeStepper` (312 LOC) |
| `ThemeManager.kt` | UI | `AppPalette → theme resource`, `livePalette` Compose state (palette swap without recreate), `NightMode` → `AppCompatDelegate`, theme-attribute colour resolution (system bars) (52 LOC) |
| `LanguageManager.kt` | UI | stored app language (`corealert_prefs`, key `app_language`): `current(context)`, `apply(context, …, activity)` (refreshes the `Application` **and** activity resources, then notifies the activity), `reapply(context)` (re-imposes it after a system config change), `wrap(base)` used by `CoreAlertApp` / `BaseActivity.attachBaseContext` (60 LOC) |

`res/` contents: 25 vector drawables, 5 TTFs + `manrope.xml`, `values/` (5 files:
`strings.xml` 180 en / `themes.xml` / `colors.xml` / `colors_palettes.xml` /
`attrs_tokens.xml`), `values-my/` (179 Burmese + 1 plural), `values-night/` (2), `xml/` (3),
`mipmap-anydpi-v26/` (2).

---

## 8. Data types

| Type | File | Definition |
|---|---|---|
| `VipContact` | `domain/models/VipContact.kt` | `name, number, ringtoneEnabled=true, callAlertMode=INHERIT` |
| `QuietRule` | `domain/models/QuietRule.kt` | `days: Set<Int>, startHour, startMinute, endHour, endMinute` |
| `AppPalette` | `domain/models/AppPalette.kt` | `INDACO, TEAL, ARGILLA, ARDESIA` (+ `fromStoredOrdinal`) |
| `BuildInfo` | `domain/models/BuildInfo.kt` | `isDebug` — fed from `BuildConfig` by `appModule` |
| `CallAlertMode` | `domain/models/CallAlertMode.kt` | `INHERIT, FIRST, SECOND` |
| `CallAlertDecision` | `domain/models/CallAlertDecision.kt` | `shouldRing, volumePercent, exactVolume` |
| `MessageApp` | `domain/models/MessageApp.kt` | `WHATSAPP("whatsapp"), GOOGLE_MESSAGES("google_messages"), TELEGRAM("telegram"), VIBER("viber")` |
| `MessageAlertState` | `domain/models/MessageAlertState.kt` | `UNPAIRED, PAIRING, PAIRED` |
| `PendingMessagePairing` | `domain/models/PendingMessagePairing.kt` | `number, app` |
| `NightMode` | `domain/models/NightMode.kt` | `FOLLOW_SYSTEM(-1), LIGHT(1), DARK(2)` (+ `storedValue`, `fromStored`) |
| `HomeWarning` | `policy/HomeWarningPolicy.kt` | `NONE, PERMISSIONS_REVOKED, AUTO_REVOKE, BATTERY_UNRESTRICTED_NEEDED` |
| `ContactPhoneOption` | `policy/ContactImportPolicy.kt` | `number, label` |
| `AppConfig` | `data/ConfigExporter.kt` | `version, exportedAt, volumePercent, overrideSoundType, messageVolumePercent, messageSoundEnabled, messageSoundType, contacts, quietRules, callAlertMode, repeatCallWindowMinutes, escalateCallVolume` |

---

## 9. Tests (22 files / 117 tests)

| Module | Files | Tests | Location |
|---|---|---|---|
| `:core:domain` | 2 | 20 | `core/domain/src/test/…` |
| `:core:policy` | 15 | 69 | `core/policy/src/test/…` |
| `:core:data` | 3 | 23 | `core/data/src/test/…` |
| `:core:monitoring` | 1 | 2 | `core/monitoring/src/test/…` |
| `:feature` | 1 | 3 | `feature/src/test/…` (`AppRouteTest`) |

Tests live with the code they cover; `./gradlew test` runs every module that has tests
(both build types). `:app` has no unit tests.

See [build-release-testing.md](build-release-testing.md#8-test-suite) for the full list.
