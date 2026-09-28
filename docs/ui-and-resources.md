# UI & resources

Single-activity architecture rendered entirely with **Jetpack Compose (Material 3)**, a
navigation drawer, four colour palettes and two locales. There are **no XML layouts, no
menus, no `res/color` selectors and no ViewBinding** left in the app — every screen, card,
row and dialog is a `@Composable`, and every dialog is a Material 3 `Dialog`.

Two modules split this layer: **`:core:ui`** owns *every* `res/` entry plus the theme and
the shared components (`ui/theme/`, `ui/component/`, `ThemeManager`), while the five
screens, their `ViewModel`s and the navigation shell live in **`:feature`**
(`home/`, `settings/`, `privacy/`, `navigation/`) so the screens can own the Activity
result launchers. **`:app`** keeps only the activity/application shell and mounts the
feature's root composable.

---

## 1. Screens & navigation

### 1.0 Activity shell

| File | Role |
|---|---|
| `BaseActivity.kt` (59 LOC) | `AppCompatActivity` base: applies the palette theme (`ThemeManager.themeResId(theme.palette)`, `ThemeRepository` from Koin) and syncs `ThemeManager.livePalette` **before** `super.onCreate`, applies the stored app language in `attachBaseContext` (`LanguageManager.wrap`), enables edge-to-edge (`WindowCompat.setDecorFitsSystemWindows(window, false)`), repaints system bars (`applySystemBars()`) on create / resume / configuration change / window focus; `onConfigurationChanged` re-applies the stored language, repaints the bars and dispatches the new `Configuration` to the view tree (`decorView.dispatchConfigurationChanged`) so Compose re-reads strings and colours **without recreating the activity** |
| `MainActivity.kt` (63 LOC) | Compose host: resolves `AlertSettingsRepository` from Koin, keeps `HomeViewModel` with `by viewModel()` for the lifecycle callbacks, mounts `setContent { CoreAlertTheme(palette = ThemeManager.livePalette) { CoreAlertRoot() } }` — `livePalette` is Compose state, so palette changes recompose the theme in place (all navigation state lives in `:feature`) |

`feature/navigation/CoreAlertRoot.kt` (237 LOC, `:feature`) is the root composable:

* `enum AppRoute(route, labelRes, iconRes)` (`feature/navigation/AppRoute.kt`) declares the
  three destinations: `HOME` (`"home"`), `SETTINGS` (`"settings"`), `PRIVACY` (`"privacy"`);
  `AppRoute.fromRoute(...)` falls back to `HOME`.
* Navigation-Compose: `rememberNavController()` + `NavHost` (start destination `home`) hosts
  `HomeScreen(homeState, home::handleAction)` / `SettingsScreen(settingsState, settings::handleAction)` /
  `PrivacyScreen()`; the current
  back-stack entry drives the top-bar title, the drawer selection and the Home-only FAB.
* Drawer ↔ back-stack sync uses the standard top-level-destination pattern:
  `navigate(route) { popUpTo(startDestination) { saveState = true }; launchSingleTop = true;
  restoreState = true }`, so re-selecting a drawer item never duplicates a destination and
  each destination's state is restored on return.
* `ModalNavigationDrawer` → `Scaffold`: `TopAppBar` (title per route, hamburger opens the
  drawer), a `FloatingActionButton` shown **only on Home** (`home.handleAction(HomeAction.OpenAddChoice)`, accent
  container).
* Back behaviour: one `BackHandler` closes the open drawer; otherwise Navigation handles
  the stack (destination → Home → exit the activity).
* `AppDrawer` = `ModalDrawerSheet` (zero window insets, `statusBarsPadding()` inside the
  header) + `DrawerHeader` (bell badge, app name, subtitle) + three
  `NavigationDrawerItem`s built from `AppRoute.entries`.
* The two view models are obtained **once, activity-scoped**, with `koinViewModel()`
  (`SettingsViewModel` receives `{ home.reloadContacts() }` through `parametersOf`); they are
  registered in `featureModule`. `CoreAlertRoot` is the only composable that touches them:
  it collects `home.state` / `settings.state` with `collectAsState()` and passes the
  immutable `UiState` plus an action lambda down the tree.

`MainActivity.onResume()` does one extra thing:

* **monitoring self-heal** — if `settings.isServiceEnabled` is true but `CallMonitorService`
  is not running (crash, OEM battery kill), it restarts the service. Without
  this, the hero switch keeps showing "on" while nothing is listening for calls.

`MainActivity.onConfigurationChanged()` (the config dispatch fired by `BaseActivity`) calls
`home.refreshStrings()`, which rebuilds `callModeSummary` and `muteCountdown` in place —
both are resolved through the `Application` resources, so without this an in-app language
switch would leave them in the old language until the next resume (the recreated activity
used to do that for free).

### 1.1 Home (`feature/home/HomeScreen.kt`, 83 LOC + `feature/home/HomeViewModel.kt`, 467 LOC + `feature/home/HomeUiState.kt`, 66 LOC + `feature/home/HomeAction.kt`, 34 LOC + `feature/home/component/`, 8 files / 1 221 LOC)

`HomeScreen.kt` holds only the `HomeDialog` union and the `LazyColumn` shell (with the
`RequestMultiplePermissions` / `PickContact` launchers registered in
`component/PermissionsCard.kt` and `component/HomeDialogs.kt`); every card and dialog
lives as its own file in `feature/home/component/` (`WarningBanner.kt`, `HeroCard.kt`,
`CallModeSummaryButton.kt`, `MuteCard.kt`, `PermissionsCard.kt`, `ContactRow.kt`,
`EmptyContactsCard.kt`, `HomeDialogs.kt`), each taking `(state, onAction)` — `internal
fun`, file-private helpers stay `private`.


A `LazyColumn` with stable item keys:

| Key | Composable | Contents |
|---|---|---|
| `warning` | `WarningBanner` | shown only when `HomeWarningPolicy` decides there is something to fix: icon, message, action button (battery-optimisation / app-details intent fired from `LocalContext`) |
| `hero` | `HeroCard` + `HeroBell` + `HeroChip` + `DrawScope.drawPulseRing` | full-bleed state card: gradient, animated pulse ring (only while monitoring), bell icon (on/off), state label (28 sp ExtraBold), hint text, service switch, bypass chip |
| `callmode` | `CallModeSummaryButton` | "first / second call" summary → opens the `CallModeSettings` dialog |
| `mute` | `MuteCard` | pause button + live mute countdown |
| `permissions` | `PermissionsCard` + `PermissionRow` | runtime-permission and DND rows, each with status icon and action button |
| `contacts_header` + contact keys | `ContactRow` + `ContactMenu` | initials avatar, name, number, overflow menu (edit / call mode / message alerts / delete) |
| `empty` | `EmptyContactsCard` | empty-state card when `contacts.isEmpty()` |

State is held by **`HomeViewModel`** (`feature/home/HomeViewModel.kt`, an
`androidx.lifecycle.ViewModel` declared in `featureModule` and obtained in `CoreAlertRoot`
with `koinViewModel()`; `MainActivity` reuses the same instance via `by viewModel()`), but
the screen never sees the model: `CoreAlertRoot` collects `home.state`
(`StateFlow<HomeUiState>`, defined in `feature/home/HomeUiState.kt`) with `collectAsState()`
and `HomeScreen(state, onAction)` passes only immutable data plus an action lambda.

* `HomeUiState` is an immutable `data` class mirroring the repositories: `contacts` (from
  `ContactRepository`), `serviceEnabled`, `runtimeGranted`, `dndGranted`,
  `contactsPermissionGranted`, `warning`, `muteCountdown`, `callModeSummary`, the call-alert
  slice (`callAlertMode`, `repeatWindowMinutes`, `escalateCallVolume`, `volumePercent`),
  `messageStates`, `dialog`.
* Every interaction is a `HomeAction` (`ServiceToggled`, `PermissionResult`, `OpenDndDialog`,
  `ToggleMute`, `ActivateMute`, add/edit/delete contact, call-alert setters, message pairing,
  `CloseDialog`, …) dispatched through `onAction` → `HomeViewModel.handleAction`. Writes go through
  `setState { … }`, which re-runs `derive()` so the call-mode summary, the call-alert slice
  and the per-contact message states always reflect the repositories.
* `CoreAlertRoot` dispatches `HomeAction.Refresh` from a `LaunchedEffect` whenever the `HOME`
  route becomes current; it runs the same resync as `MainActivity.onResume()` (permissions,
  service flag, contacts, call-mode summary + mute countdown), so anything changed on the
  Settings screen or in system settings pages is re-derived on the way back.
* The Activity-result launchers are registered **inside the composables**
  (`rememberLauncherForActivityResult` in `PermissionsCard` and `HomeDialogs`) and hand
  their results back as actions (`PermissionResult(results)`, `ContactPicked(uri)`). The
  `runtimePermissions` list lives in `HomeUiState.kt` and `state.contactsPermissionGranted`
  tells the screen whether to request or to open the picker.
* Deep links to system settings (notification-policy access, notification listener,
  battery optimisation exemption) fire from `LocalContext` in the composable, so no
  `Activity` reference is kept by the model (no `StaticFieldLeak`).
* One `Handler` loop posts every 60 s **only while muted** (`countdownHandler` /
  `countdownRunnable`) so the mute countdown never goes stale; `onResume()`/`onPause()`
  (forwarded by the activity) restart/stop it, `refreshStrings()` (also driven by the
  activity's config change) re-derives `callModeSummary` and rebuilds `muteCountdown`, and
  `onCleared()` cancels it.
* Runtime permissions requested: `READ_PHONE_STATE`, `READ_CALL_LOG`, `READ_CONTACTS`,
  `POST_NOTIFICATIONS` (API ≥ 33).
* **Contact CRUD**: add-choice dialog (phonebook vs manual) → `PickContact` launcher →
  multi-number selection dialog (`ContactImportPolicy.uniqueOptions` / `createVipContacts`,
  `plurals.contact_numbers_added`) → edit/remove confirm dialogs →
  `contactRepository.saveContacts(...)` + `CallMonitorService.refreshNotification()`.
* **Message-alert state machine**: `MessageApp` pairing driven by `MessageAlertTap` /
  `BeginMessagePairing` / `CancelMessagePairing` → unpair / cancel /
  notification-listener access.
* Permission health feeds `HomeWarningPolicy.decide(...)`, which drives the warning banner
  (critical permissions, auto-revoke, battery optimisation).
* Because `dialog` lives in `HomeUiState`, an open dialog **survives rotation** instead of
  being rebuilt by the activity.

### 1.2 Settings (`feature/settings/SettingsScreen.kt`, 82 LOC + `feature/settings/SettingsViewModel.kt`, 288 LOC + `feature/settings/SettingsUiState.kt`, 61 LOC + `feature/settings/SettingsAction.kt`, 34 LOC + `feature/settings/component/`, 8 files / 964 LOC)

`SettingsScreen.kt` holds only the `SettingsDialog` union, the column shell (with the
`CreateDocument` / `OpenDocument` launchers) and `exportFileName()`; every card and
dialog below lives as its own file in `feature/settings/component/`
(`AppearanceCard.kt`, `MessageAlertsCard.kt`, `VolumeCard.kt`, `SettingsControls.kt`,
`SoundTypeCard.kt`, `QuietHoursCard.kt`, `BackupCard.kt`, `SettingsDialogs.kt`), each
taking `(state, onAction)` — `internal fun`, file-private helpers stay `private`.

| Card | Controls |
|---|---|
| `AppearanceCard` | four `SwatchEntry` circles (colour drawn in code, 3 dp selected stroke) → `SelectPalette` action → `ThemeManager.livePalette`; night-mode `ModeToggle` (system / light / dark) → `SelectNightMode` → `AppCompatDelegate` rewrites the activity resources in place; language `LanguageToggle` (system / English / မြန်မာ) → `SelectLanguage(language, activity)` → `LanguageManager.apply` + `onConfigurationChanged` |
| `MessageAlertsCard` | sound switch, volume slider, sound-type radio group — hidden if `!VipMessageAlerts.supported`, alpha 0.45 when disabled |
| `VolumeCard` | ringtone volume slider + value pill (25–100) + shared `CallAlertSettingsContent` (first/second call, repeat window 3/5/10, escalation switch, preview) |
| `SoundTypeCard` | override sound: ringtone vs notification |
| `QuietHoursCard` | rule rows (`QuietRuleRow`) + add button → `AddQuietRuleDialog` (7 weekday `DayChip`s, `TimeStepper` from/to, cross-midnight hint) |
| `BackupCard` | export / import buttons with progress + result feedback |

Export/import is split over two places:

* **`SettingsScreen`** owns the `ActivityResultContracts.CreateDocument("application/json")`
  and `OpenDocument` launchers plus the password prompt with confirmation: a valid prompt
  dispatches `ConfirmExportPassword(password)` (the model stores it) and launches the
  document picker with its own `corealert-backup-<timestamp>.json` name, then feeds the
  picked URI back as `ExportUri(uri)` / `ImportUri(uri)`.
* **`SettingsViewModel`** (`feature/settings/SettingsViewModel.kt`) holds the dialog state and the
  file work: `ExportUri` writes the encrypted envelope through `ContentResolver`,
  `ImportUri` reads/validates it, and `DecryptPendingImport` runs the
  PBKDF2 decrypt **off the UI thread**, posting the result back to the main looper.
  `applyImportedConfig()` restores everything and restarts the service if it was enabled.
  Palette changes go through `SelectPalette`, which stores the pref and updates
  `ThemeManager.livePalette`; night mode and language are applied just as live — the app
  never calls `activity.recreate()`.

Dialog state is a `sealed interface SettingsDialog`: `ExportPassword`, `ImportConfirm`,
`ImportPassword`, `AddQuietRule`, `DeleteQuietRule(index, rule)` — held in
`SettingsUiState.dialog`, so it survives rotation.

### 1.3 Other screens

* **`PrivacyScreen`** (`feature/privacy/PrivacyScreen.kt`, 46 LOC): fully static — a `Column`
  of three `PrivacyCard`s from `feature/privacy/component/PrivacyCard.kt` (103 LOC). Each card
  has a `CardHeader` (`ic_shield` / `ic_lock` / `ic_user`), an `OkChip`
  (`privacy_chip_no_tracking` / `privacy_chip_encrypted` / `privacy_chip_no_ads`) and its body;
  `linkedBody()` renders the body as an `AnnotatedString` whose URLs, e-mail addresses and
  phone numbers carry a `LinkAnnotation.Url` (accent colour + underline) opened through
  `LocalUriHandler`. Nothing here is stateful, so the screen has no `UiState` / `Action` /
  view model.

### 1.4 Dialogs

All dialogs are Material 3 `Dialog`s built from the shared primitives in
`:core:ui` → `ui/component/Dialogs.kt` (312 LOC).

| Primitive | Used for |
|---|---|
| `SosDialog` | confirm/cancel dialogs (remove contact, DND explain, message access/pair, import confirm) with optional body composable |
| `ListDialog` | single-pick lists (add contact choice, quiet-rule time presets) |
| `SingleChoiceDialog` | radio lists (per-contact call mode, quiet-day presets, sound type) |
| `MultiChoiceDialog` | multi-number selection when importing a contact |
| `SosTextField` | text inputs (name, number, backup password) |
| `TimeStepper` | ± hour/minute steppers (mute hours, quiet-rule from/to) — replaces the old `NumberPicker` / `MaterialTimePicker` |
| `OutlinedChoiceButton` | secondary buttons inside dialogs |

Home dialog dispatch lives in `HomeDialogs(state, onAction)` (`feature/home/component/HomeDialogs.kt`)
over `sealed interface HomeDialog`: `AddChoice`, `ContactForm`, `ContactNumbers`,
`RemoveContact`, `ContactCallMode`, `CallModeSettings`, `MuteHours`, `DndExplain`,
`MessageAccess`, `MessagePair`. The dedicated ones (`ContactFormDialog`,
`ContactNumbersDialog`, `MuteHoursDialog`, `CallModeSettingsDialog` +
shared `CallAlertSettingsContent`) live in `feature/home/component/HomeDialogs.kt`;
`component/VolumeCard.kt` imports `CallAlertSettingsContent` from that file (the two
screens share it) and drives it from its own state with `SettingsAction.SetCallAlertMode` /
`SetRepeatWindow` / `SetEscalateVolume`.

Confirm buttons whose action can fail (save contact, save numbers, add quiet rule) return
`false` to `SosDialog`, so dismissal is decided by the view model: it clears `dialog` on
success and leaves the dialog open on a validation toast.

---

## 2. Reusable components

`ui/component/Common.kt` (216 LOC, `:core:ui`):

| Component | Purpose |
|---|---|
| `Overline` | uppercase section label (11 sp, bold, letter-spaced, `inkSecondary`) |
| `AppCard` / `CardContent` | the standard surface card: 20 dp corners, hairline border, no elevation |
| `CardHeader` | icon badge + title (+ optional trailing slot) used by every card |
| `IconBadge` | circular tinted icon container |
| `SoftPill`, `ValueBadge` | small status/value chips |
| `StatusText` | granted/missing status line coloured `statusOk` vs `statusMissing` |
| `SecondaryText`, `GroupTitle`, `VGap` | supporting text, group headings, vertical spacing |
| `RadioRow` | label + `RadioButton` row used by all single-choice dialogs |

---

## 3. Theming

### 3.1 Structure

```
core/ui/src/main/java/…/ui/theme/CoreAlertTheme.kt   ← the Compose theme (colours/typography/shapes)
  Manrope: FontFamily(manrope_regular|medium|semibold|bold|extrabold)
  CoreAlertTypography             ← Material 3 Typography restyled with Manrope
  CoreAlertShape                  ← 14 / 20 / 26 dp (+ Pill)
  CoreAlertColors + LocalCoreAlertColors
  CoreAlertTheme { … }            ← builds ColorScheme + provides CoreAlertColors

core/ui/src/main/res/values/themes.xml           ← Theme.CoreAlert.Base + Indaco|Teal|Argilla|Ardesia
                                   (window/activity theme, system-bar attributes)
core/ui/src/main/res/values/attrs_tokens.xml     ← 5 custom attributes (tokenAccent, tokenFaded,
                                   tokenStatusOk, tokenHeroBg, tokenHeroChipBg)
core/ui/src/main/res/values/colors.xml           ← base colours (light)
core/ui/src/main/res/values-night/colors.xml     ← base colours (dark)
core/ui/src/main/res/values/colors_palettes.xml       ← 4 palettes × 10 roles = 40
core/ui/src/main/res/values-night/colors_palettes.xml ← night variants of the same 40
```

**How colours reach Compose.** `CoreAlertTheme(palette, content)` receives the palette from
the caller (`MainActivity` passes `theme.palette`), maps it to the palette's ten
`R.color.*` ids (`paletteRes()`), and builds the `MaterialTheme`
`ColorScheme` with `colorResource(...)`. Because the palette colours have night variants in
`values-night`, day/night switching is handled automatically; `ThemeManager.isDark()`
selects `lightColorScheme` vs `darkColorScheme`. The extra values Material 3 has no slot
for (accent, status colours, hero colours, ink/bg/surface aliases) are exposed as
`CoreAlertColors` through `LocalCoreAlertColors` (`coreAlertColors()` accessor).

> Theme attributes are **not** read from library R classes any more. Resolving
> `com.google.android.material.R.attr.colorPrimary` from Compose was fragile (that
> attribute belongs to AppCompat, and not every Material version re-declares it), so the
> theme works purely from app-owned colour resources. `attrs_tokens.xml` is still declared
> for XML-side parity; only `ThemeManager.color(context, android.R.attr.colorBackground)`
> is used in code (system bars).

`Theme.CoreAlert.Base` (parent `Theme.Material3.DayNight.NoActionBar`) still sets the
Manrope family and system-bar colours; the four palette themes add the palette colours and
tokens. There is **no** `values-night/themes.xml` — dark mode is handled purely by colour
overrides.

### 3.2 Palettes

`enum AppPalette { INDACO, TEAL, ARGILLA, ARDESIA }`, stored as an ordinal
(`theme_palette`, default `INDACO`), applied by
`ThemeManager.themeResId(palette)` → `Theme_CoreAlert_*`, set in `BaseActivity.onCreate`
**before** `super.onCreate`, which also syncs the live copy. Changing the palette in
Settings writes the pref and updates `ThemeManager.livePalette`; `MainActivity` observes
that state in `setContent`, so `CoreAlertTheme` recomposes with the new ids in place and
the activity keeps its navigation state.

Each palette defines 10 roles: `primary, accent, container, on_container, faded,
status_ok, secondary, secondary_bg, hero_bg, hero_chip_bg`.

Night variants lighten the primaries and darken the containers, e.g.
`indaco_primary #3D4DB7 → #7C8AF0`, `teal #0F8A80 → #45C7B8`,
`argilla #C0563B → #E8836A`, `ardesia #2F5E8C → #7FB0DC`.

Night mode: `theme_mode` stores `NightMode.storedValue` — `FOLLOW_SYSTEM` (-1, default),
`LIGHT` (1), `DARK` (2); `ThemeManager.applyNightMode` only calls `AppCompatDelegate`
when the value changes. Because `MainActivity` declares `android:configChanges="uiMode"`,
the delegate rewrites the activity resources (and re-applies the theme) in place and
calls `onConfigurationChanged` instead of recreating the activity.

### 3.3 System bars

`BaseActivity.applySystemBars()` runs in `onCreate`, `onResume`, `onConfigurationChanged`
and `onWindowFocusChanged(true)`: status/nav bars ← `?android:colorBackground`, contrast
enforcement disabled, light/dark icons flipped from `ThemeManager.isDark()`. This is what
changelog 42 calls *"correct status/nav bar contrast when overriding mode"*.

The same `onConfigurationChanged` hook pushes the configuration down to the views
(`window.decorView.dispatchConfigurationChanged(...)`): `AndroidComposeView` then replaces
its `LocalConfiguration` state, which every `stringResource` / `painterResource` reads, so
the whole Compose tree refreshes without an activity restart.

On top of that the window is edge-to-edge; `Scaffold` applies its own padding and
`ModalDrawerSheet` is given `WindowInsets(0)` so the drawer header can handle
`statusBarsPadding()` itself.

---

## 4. Resources inventory

Everything below lives in **`:core:ui`** at `core/ui/src/main/res/` — there is no `res/`
directory anywhere else (`:app` declares none). Paths in the table are relative to it.

| Directory | Count | Notes |
|---|---|---|
| `res/drawable/` | 25 | all `ic_*` vector icons (`bg_*`, `nav_item_bg`, `pulse_ring`, `swatch_*` were deleted with the XML UI) |
| `res/font/` | 6 | `manrope_{regular,medium,semibold,bold,extrabold}.ttf` (loaded per weight by the Compose `FontFamily`) + `manrope.xml` family mapping |
| `res/mipmap-anydpi-v26/` | 2 | adaptive `ic_launcher`, `ic_launcher_round` |
| `res/values/` | 5 | `strings.xml`, `colors.xml`, `colors_palettes.xml`, `attrs_tokens.xml`, `themes.xml` |
| `res/values-my/` | 1 | `strings.xml` (Burmese) |
| `res/values-night/` | 2 | `colors.xml`, `colors_palettes.xml` |
| `res/xml/` | 3 | `backup_rules`, `data_extraction_rules`, `locales_config` |

**Removed with the Compose migration:** `res/layout/` (10 layouts), `res/menu/`
(`nav_menu`, `vip_row_menu`), `res/color/` (4 selectors), 17 drawables (`bg_*`,
`nav_item_bg`, `pulse_ring`, `swatch_*`), the `Widget.CoreAlert.*` and
`TextAppearance.CoreAlert.*` styles, and the `privacy_title` string.

Drawable groups now:

* **Navigation / actions:** `ic_home`, `ic_sliders`, `ic_info`, `ic_menu`, `ic_chevron`,
  `ic_more_vert`, `ic_plus`, `ic_trash`, `ic_download`, `ic_upload`, `ic_phone`, `ic_lock`.
* **Status:** `ic_bell`, `ic_bell_off`, `ic_check`, `ic_alert`, `ic_shield`,
  `ic_shield_check`, `ic_clock`, `ic_volume`, `ic_notification_sos`.
* **Theme:** `ic_sun`, `ic_moon`, `ic_user`.
* **Palette swatches:** drawn in code — `SwatchEntry` renders a `Box` with the palette
  colour (`R.color.<palette>_primary`) in a circle, so no raster/shape drawable is needed.
* **Misc:** `ic_launcher_foreground`.

The hero glow/pulse are no longer drawables: the pulse ring is drawn with `Canvas`
(`DrawScope.drawPulseRing`) and the glow is a `Brush.linearGradient`.

### XML configs

| File | Content |
|---|---|
| `backup_rules.xml` | excludes `corealert_prefs.xml` |
| `data_extraction_rules.xml` | same, from cloud backup **and** device transfer |
| `locales_config.xml` | `en`, `my` (per-app language picker, API 33+) |

---

## 5. Localisation

| File | Strings |
|---|---|
| `core/ui/src/main/res/values/strings.xml` | **180** `<string>` + 1 `<plurals>` (`contact_numbers_added`); only `app_name` is `translatable="false"` |
| `core/ui/src/main/res/values-my/strings.xml` | 179 (Burmese) + the same `<plurals>` — same names, same format placeholders |

There are no flavor-specific string files — both builds share the same two files. Italian
(`values-it/`) was dropped when the app moved to English + Burmese.

String sections in the main file (by line range):

| Lines | Section |
|---|---|
| 3–18 | app identity, palettes, night modes, language, subtitle |
| 19–28 | monitoring/hero labels, privacy chips |
| 29–75 | permissions, contacts, contact CRUD + plurals, DND dialog, service/override notification text |
| 76–96 | quiet hours (incl. `day_mon…day_sun`, `quiet_every_day`, `quiet_next_day`) |
| 97–100 | empty section markers left from the original project + the `Mute Timer` header |
| 101–108 | mute timer |
| 109–116 | settings sound + navigation |
| 118–132 | security/backup, DND-degraded warning |
| 133–162 | privacy, row/menu descriptions, home warnings, notification channels, backup password prompts |
| 163–188 | VIP message alerts |
| 189–201 | call mode / repeat call / volume escalation |

> The VIP message-alert blocks ship in **every** flavor: the message-alert listener is
> declared in the **main** manifest and `VipMessageAlertsProvider.supported` is `true`.

Language switching is in-app: **Settings → Appearance → Language** offers *System*,
*English* and *မြန်မာ* (`LanguageToggle`). The choice is stored in `corealert_prefs`
(key `app_language`) and applied by `LanguageManager`: `wrap(base)` in
`CoreAlertApp` / `BaseActivity.attachBaseContext` builds the locale-specific context,
`apply(..., activity)` refreshes the `Application` **and** activity resources, then
notifies the activity; the `onConfigurationChanged` hook above dispatches the new locale
to the view tree, so the UI switches language in place (service and toast strings come
from the already-updated `Application` resources). `LanguageManager.reapply(...)` in that
same hook re-imposes the stored locale whenever the framework hands back the system one.
`android:localeConfig` keeps the API 33+ system per-app language picker available
for the *System* option.

---

## 6. Accessibility & UX details

* `contentDescription` on every icon-only control (`add_contact`, `more_icon_desc`, …).
* `plurals` for *"N numbers added"* rather than string concatenation.
* Status colours: `statusOk` vs `statusMissing` for granted/missing permission rows.
* Disabled controls dimmed (alpha 0.45) rather than removed.
* The hero pulse ring only draws while monitoring is on, and stops when it is paused.
* The mute countdown re-renders every 60 s while paused, so the UI never goes stale
  without a user event.
* Back behaviour: the open drawer closes first, then Navigation pops to Home and only
  then exits the activity.
* Large tap targets: 40–46 dp avatar/overflow hit areas, `RadioRow` fills the dialog width.
