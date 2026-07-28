# AGENTS.md — Codex

## Goal
- Convert all `EditText` input fields to Compose `OutlinedTextField` (NyanTV pattern) and remove the custom TV keyboard infrastructure entirely.
- Default to AMOLED dark mode (`OledMode=1`).
- System IME works on all devices (no custom keyboard suppression).
- D-pad navigation on TV works via `onPreviewKeyEvent`.

## Progress
### Done
- All `EditText` fields converted to Compose `OutlinedTextField` across the app:
  - `dialog_edittext.xml`, `dialog_layout.xml`
  - `bottom_sheet_add_repository.xml`, `bottom_sheet_subtitle_sync.xml`, `bottom_sheet_proxy.xml`
  - `activity_crash.xml`
  - `fragment_comments.xml` (portrait + landscape)
  - `fragment_login.xml`
  - `activity_markdown_creator.xml`, `activity_player_settings.xml`
  - All search fields: `SearchAdapter`, `SupportingSearchAdapter`, `SourceSearchDialog`, `LibraryFragment`, `ListActivity`, `ExtensionsActivity`, `GifPickerBottomDialog`
  - `SettingsCommonActivity`, `ProxyDialogFragment`, `AnilistSettingsActivity`
  - `dialog_user_agent.xml`, `item_custom_list.xml`
  - `activity_settings_accounts.xml` (proxy fields)
  - `item_search_header.xml` (search bar AutoCompleteTextView)
  - `bottom_sheet_gif_picker.xml` (GIF search TextInputEditText)
  - `bottom_sheet_source_search.xml` + `SourceSearchDialogFragment`, `LocalMappingSearchDialog`
- Custom keyboard infrastructure completely removed:
  - Deleted: `TvKeyboardUtil.kt`, `TvKeyboardView.kt`, `tv_keyboard_view.xml`, `tv_keyboard_compact.xml`
  - Removed `KeyboardMode` from `Preferences.kt`
  - Removed `TvKeyboardKey` styles from `style.xml`
  - Removed keyboard drawables
  - Removed all `TvKeyboardUtil` references from `MainActivity.kt`, `SearchAdapter.kt`, `SupportingSearchAdapter.kt`, `LibraryFragment.kt`, `ListActivity.kt`, `ExtensionsActivity.kt`, `SettingsCommonActivity.kt`, `GifPickerBottomDialog.kt`, `SourceSearchDialog.kt`, `ProxyDialogFragment.kt`
  - `isTv()` inlined in `SubscriptionNotificationTask.kt`, `CommentNotificationTask.kt`, `AnilistNotificationTask.kt`
  - Removed dead `searchKeyboardToggle` button from `item_search_header.xml`
- Deleted unused `dialog_repositories.xml` layout
- Removed dead `InputMethodManager` imports from `LibraryFragment.kt`, `ExtensionsActivity.kt`, `ListActivity.kt`
- Text colors: all `?attr/colorPrimary` → `?attr/colorOnSurface` across 21 layout files + `style.xml` `HeadingText` style
- Background fixes: added `?attr/colorSurface` roots to `activity_list.xml`, `activity_notification.xml`, `activity_profile.xml` + land, `fragment_profile.xml`, `fragment_extensions.xml`, `activity_extensions.xml`, `fragment_notifs.xml`
- Side rail buttons: `?attr/colorOnSurface` → `@android:color/white` (all text + tints except Clear Cache/Log Out)
- Provider text: `#FFFFFFFF` → `?attr/colorOnSurface` in `item_provider.xml`
- Episode/anime card text: `@android:color/white` / `@color/bg_white` → `?attr/colorOnSurface` in card layouts
- Comments list editor button: textColor + strokeColor → `?attr/colorOnSurface`
- Rating badge: `item_score.xml` fully rounded (`corners android:radius="12dp"`)
- OLED default: `OledMode` default `0` → `1` (AMOLED on by default)
- Keyboard caps lock: added `keyCapsLock` to full + compact layouts, `toggleCapsLock()` in `TvKeyboardView.kt`, lowercase defaults, fixes for symbol-mode return
- Anime list button removed from `drawer_right_rail.xml` + `setupRightRail()` in `MainActivity.kt`
- Splash: `fitCenter` → `centerCrop`, `?android:colorBackground` → `#FF000000` on both portrait and landscape
- Build fixes: duplicate background in `activity_settings_accounts.xml`, missing `R.id.rightRailAnimeList`, `mapOf` type inference, caps lock NPE in compact layout

## Key Decisions
- Compose `OutlinedTextField` replaces ALL `EditText` — no hybrid View/Compose; custom keyboard fully deleted.
- System IME works on all devices (TV + phone) since `showSoftInputOnFocus = false` and `InputMethodManager` suppression are removed.
- `onPreviewKeyEvent` handles D-pad navigation after text field focus (NyanTV pattern: `Key.DirectionDown` → `FocusDirection.Down`).
- AMOLED is default dark mode for true black backgrounds.
- AutoCompleteTextView dropdown selectors (media list filters in `bottom_sheet_media_list*.xml`) are NOT converted — they are selection widgets, not text input fields. The search bar (`item_search_header.xml`) was converted since it acted as a text input on Fire TV.

## Critical Context
- All `EditText` text is managed via Compose `mutableStateOf` in each fragment/activity.
- Splash white margins were caused by `fitCenter` + `?android:colorBackground` (white in light mode) — fixed by `centerCrop` + `#FF000000`.
- `KeyboardMode` preference entry deleted entirely — no more mode switching.
- `searchKeyboardToggle` button removed from layout since no Kotlin code references it.

## Relevant Files
- All Compose-based layouts: files using `<androidx.compose.ui.platform.ComposeView>` with inline `@Composable` functions containing `OutlinedTextField` + `onPreviewKeyEvent`
- Deleted: `TvKeyboardUtil.kt`, `TvKeyboardView.kt`, `tv_keyboard_view.xml`, `tv_keyboard_compact.xml`
- `Preferences.kt` — `OledMode` default `0→1`, `KeyboardMode` removed
- `MainActivity.kt` — no keyboard mode references, no anime list button
- Notification tasks: inline `isTv()` function
