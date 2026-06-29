# Subtitle Window for OBS Streaming — Design

**Date:** 2026-06-29
**Status:** Draft (pending user review)

## Goal

Add a second Compose Desktop window to BotakTTS that displays the text currently being spoken by the TTS engine, so streamers can capture it in OBS as a subtitle/caption overlay for viewers who prefer to read along.

## Requirements

The subtitle window must be:

- **Transparent** — no visible background, so it composites cleanly over any OBS scene.
- **Resizable** — the user sizes it to fit their scene; long text wraps within the window.
- **Movable** — the user positions it anywhere on screen (drag, like the existing `AppWindow`).
- **OBS-friendly** — classic subtitle styling: white text with a black outline/shadow, centered, multi-line wrapped.
- **Synced to playback** — text appears only while TTS is actively speaking; cleared when playback stops.
- **Tray-toggleable** — shown/hidden via a system tray menu item ("Show Subtitles"), not via hotkey.
- **Persistent** — its enabled flag, position, and size are saved to config and restored on next launch.

## Non-Goals (YAGNI)

- No karaoke-style word-by-word highlighting. Full text appears at once, wrapped to fit.
- No automatic show/hide tied to audio start/stop — visibility is a manual tray toggle; when enabled, the window is always present (empty/transparent when nothing is playing).
- No hotkey for the subtitle window (the existing `Ctrl+Shift+H` hotkey remains scoped to the main input window).
- No font/color/size customization UI in this iteration — styling is fixed to the classic subtitle look. Customization can be a follow-up if needed.
- No automated UI tests for the window composable (the project has no Compose UI test infrastructure; window rendering is verified manually, consistent with existing windows).

## Architecture

Three new pieces, plus small extensions to existing components:

1. **`AppState`** — a new client-side state holder exposing the "now playing" text as a `StateFlow<String>`.
2. **`SubtitleWindow`** — a new Compose window that observes `AppState` and renders the subtitle.
3. **Config fields** — new subtitle-related fields on `ConfigService.UserSettings`.

The chosen approach (evaluated against two alternatives — a service-layer event bus and a direct callback chain) is **shared state via Compose `StateFlow`**: it keeps the subtitle window fully decoupled from `AppMainWindow`, follows the codebase's reactive Compose patterns, and keeps UI text-display concerns out of the audio service layer.

## Components

### `AppState` (new)

**File:** `client/src/main/kotlin/dev/botak/client/AppState.kt`

A small, dependency-free state holder that owns the "now playing" text and exposes it reactively. Designed to be extensible for future shared UI state.

```kotlin
class AppState {
    private val _nowPlayingText = MutableStateFlow("")
    val nowPlayingText: StateFlow<String> = _nowPlayingText.asStateFlow()

    fun updateNowPlaying(text: String) { _nowPlayingText.value = text }
    fun clearNowPlaying() { _nowPlayingText.value = "" }
}
```

- Instantiated once in `App.kt` (a single shared instance, like the existing `ttsService`/`audioStreamService` lazy singletons).
- Passed into `AppMainWindow` (writer) and `SubtitleWindow` (reader).

### `SubtitleWindow` (new)

**File:** `client/src/main/kotlin/dev/botak/client/windows/SubtitleWindow.kt`

A transparent, undecorated, always-on-top Compose window modeled on `AppMainWindow`/`AppWindow`, but display-only.

**Window properties:**
- `transparent = true`, `undecorated = true`, `alwaysOnTop = true`.
- Resizable (no `maximumSize` height lock, unlike `AppMainWindow` which pins height). The user freely resizes via the OS window edges — but since the window is undecorated, resizing is achieved by dragging a bottom-right resize handle rendered in-composable (see below), consistent with how `AppWindow` handles drag-to-move in-composable.
- Movable by dragging anywhere on the window (reuses the `detectDragGestures` + `MouseInfo`/`window.setLocation` pattern from `AppWindow`).
- `visible` bound to the `subtitleWindowEnabled` state held in `App.kt`.

**Rendering:**
- Observes `appState.nowPlayingText.collectAsState()`.
- When text is empty: renders nothing (fully transparent). The window stays present so OBS can keep capturing the region.
- When text is present: centered `Text` with:
  - White fill (`Color.White`), large readable font size.
  - Black outline/shadow via `drawText`-style stroke (Compose `Text` with a shadow or a custom `Modifier.paint`/`drawBehind` stroke). The classic subtitle look: white glyphs with a black halo for legibility over any background.
  - `textAlign = TextAlign.Center`, wrapping via `overflow = TextOverflow.Visible` / soft wrap enabled, so long sentences split into multiple centered lines that fit the window width.
- An in-composable resize handle (bottom-right corner) for sizing, since undecorated windows have no native resize grip.

**Persistence hooks:**
- On window move (drag end) and resize (handle drag end): write the new `x`, `y`, `width`, `height` to `ConfigService.userSettings` and call `saveUserSettings()`.
- On first composition: restore saved bounds if present; otherwise default to bottom-center of the primary screen at `600×200`.

### `AppMainWindow` (modified)

Currently `AppMainWindow` holds local `nowPlaying` state used only for its own "Now playing: …" status line. The change:

- Accept an `appState: AppState` parameter.
- In `startTTS()`, after synthesis begins (where it currently sets `nowPlaying = text`), also call `appState.updateNowPlaying(text)`.
- When playback ends or `stopTTS()` cancels, call `appState.clearNowPlaying()`.
- The local `nowPlaying` status line stays as-is (it is a separate, input-window-scoped display).

### `App.kt` (modified)

- Instantiate `val appState by lazy { AppState() }` (or `remember`).
- Hold `var subtitleWindowEnabled by remember { mutableStateOf(ConfigService.userSettings.subtitleWindowEnabled) }`.
- Pass `appState` into `AppMainWindow` and `SubtitleWindow`.
- Compose a new `SubtitleWindow(...)` whose `enabled` is bound to `subtitleWindowEnabled`.

### `ConfigService.UserSettings` (modified)

Add five new fields to the existing `UserSettings` data class (persisted as JSON via Jackson, matching the existing pattern — no separate config object):

```kotlin
data class UserSettings(
    var languageCode: String,
    var voiceName: String,
    var pitch: Double,
    var speed: Double,
    var volume: Float,
    var subtitleWindowEnabled: Boolean = false,  // new
    var subtitleWindowX: Int? = null,            // new; null = use default position
    var subtitleWindowY: Int? = null,            // new
    var subtitleWindowWidth: Int = 600,         // new
    var subtitleWindowHeight: Int = 200,        // new
)
```

New fields have defaults so existing `settings.json` files without them deserialize cleanly (Jackson fills defaults for missing fields on the data class).

`loadUserSettings()` defaults already use `application.conf`; the new boolean/position fields default inline (not driven by `application.conf`) since they are window-state, not TTS parameters. If a future need arises to externalize defaults, they can move to `application.conf` then.

### `SystemTrays` (modified)

Add a checkbox-style menu item "Show Subtitles" that:
- Reflects `subtitleWindowEnabled`.
- On toggle: updates `ConfigService.userSettings.subtitleWindowEnabled`, calls `saveUserSettings()`, and updates the `subtitleWindowEnabled` state in `App.kt` (mirrors the existing Enable/Disable toggle wiring).

## Data Flow

1. User types text in `AppMainWindow`, presses Enter → `startTTS()`.
2. `AppMainWindow` calls `appState.updateNowPlaying(text)` when playback begins.
3. `AppState` emits the text via `StateFlow`.
4. `SubtitleWindow` (collecting the flow) recomposes and renders the wrapped, centered subtitle.
5. When playback finishes or is cancelled, `AppMainWindow` calls `appState.clearNowPlaying()`.
6. `SubtitleWindow` recomposes to empty (transparent).

The subtitle window is purely a reader. All playback control logic stays in `AppMainWindow`.

## Error Handling

The subtitle feature is display-only and degrades gracefully:

- **Empty text** → window renders nothing (transparent). No error state.
- **Window creation failure** → logged via SLF4J (existing logger pattern), window absent; TTS unaffected.
- **Config read/write failure** → handled by existing `ConfigService` error handling; falls back to defaults. Corrupt saved bounds → treated as `null` → default position.
- **Text longer than fits** → wraps to multiple lines; the last visible line is clipped (ellipsis). Resizing reveals more — this is why the window is resizable.

No new error states surface to the user. If subtitles fail, the user simply sees no subtitles; TTS still works.

## Testing Strategy

Following the project's existing test conventions (kotlin.test with JUnit4 binding; see memory note on the test stack):

- **`AppStateTest`** (new): verify `updateNowPlaying` sets the flow value and `clearNowPlaying` resets it to `""`. Pure unit test, no Compose dependencies. Location: `client/src/test/kotlin/dev/botak/client/AppStateTest.kt` — `client` has a configured test sourceset (`testImplementation(kotlin("test"))`, `tasks.test { useJUnitPlatform() }`). Tests use `kotlin.test` assertions, consistent with the rest of the project (see memory note on the test stack).
- **`ConfigServiceTest`** (extended): add cases for the new `UserSettings` fields — defaults on fresh load, round-trip save/load of subtitle bounds, `null` position handling.
- **`SubtitleWindow` composable**: no automated UI test (project has no Compose UI test infrastructure). Verified manually by running `./gradlew :client:run`, toggling "Show Subtitles" in the tray, typing text, and confirming the subtitle appears/styled/moves/resizes/persists. This matches how the existing `AppWindow`/`SettingsWindow`/`UpdateWindow` are verified.

## Open Questions for Implementation

- The exact Compose technique for the black text outline (a `Text` shadow vs. a custom `drawText` stroke) is intentionally not pinned here. Either achieves the classic white-glyph-with-black-halo look; pick whichever composes cleanly and reads well against both light and dark OBS backgrounds at implementation time. This is a rendering detail, not an architectural decision.