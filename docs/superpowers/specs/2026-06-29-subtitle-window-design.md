# Subtitle Window for OBS Streaming — Design

**Date:** 2026-06-29
**Status:** Revised (ready for implementation)

## Goal

Add a second Compose Desktop window to BotakTTS that displays the text currently being spoken by the TTS engine, so streamers can capture it in OBS as a subtitle/caption overlay for viewers who prefer to read along.

## Requirements

The subtitle window must be:

- **Transparent** — no visible background, so it composites cleanly over any OBS scene.
- **Resizable** — the user sizes it to fit their scene; long text wraps within the window.
- **Movable** — the user positions it anywhere on screen (drag, like the existing `AppWindow`).
- **OBS-friendly** — classic subtitle styling: white text with a black outline/shadow, centered, multi-line wrapped.
- **Synced to playback** — text appears only while audio is actively streaming to the virtual cable (after synthesis completes); cleared when streaming stops or is cancelled. During the synthesis loading phase the window stays empty/transparent.
- **Tray-toggleable** — shown/hidden via a system tray menu item ("Show Subtitles"), not via hotkey.
- **Independent of app enable** — subtitle visibility is controlled only by the "Show Subtitles" tray toggle, not by the existing "Enabled" toggle that hides `AppMainWindow`. Streamers can keep captions visible while hiding the input window.
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

The chosen approach (evaluated against two alternatives — a service-layer event bus and a direct callback chain) is **shared state via `StateFlow`**: it keeps the subtitle window fully decoupled from `AppMainWindow`, introduces a small shared reactive holder for cross-window UI state (the codebase today uses `mutableStateOf` + callbacks within individual composables; this is the first use of kotlinx.coroutines Flow in the project), and keeps UI text-display concerns out of the audio service layer. `MutableStateFlow.value` assignments are thread-safe, so updates originating on `Dispatchers.IO` in the playback coroutine require no extra dispatcher hop.

## Components

### `AppState` (new)

**File:** `client/src/main/kotlin/dev/botak/client/AppState.kt`

A small, dependency-free state holder that owns the "now playing" text and exposes it reactively. Designed to be extensible for future shared UI state. All public declarations require KDoc per project conventions (`AGENTS.md`).

```kotlin
class AppState {
    private val _nowPlayingText = MutableStateFlow("")
    val nowPlayingText: StateFlow<String> = _nowPlayingText.asStateFlow()

    fun updateNowPlaying(text: String) { _nowPlayingText.value = text }
    fun clearNowPlaying() { _nowPlayingText.value = "" }
}
```

- Instantiated once in `App.kt` via `remember { AppState() }` inside the `application { }` block (UI-scoped state, not a top-level `lazy` singleton like `ttsService`/`audioStreamService`).
- Passed into `AppMainWindow` (writer) and `SubtitleWindow` (reader).

### `SubtitleWindow` (new)

**File:** `client/src/main/kotlin/dev/botak/client/windows/SubtitleWindow.kt`

A transparent, undecorated, always-on-top Compose window modeled on `AppMainWindow`/`AppWindow`, but display-only. All public declarations require KDoc per project conventions (`AGENTS.md`).

**Window properties:**
- `transparent = true`, `undecorated = true`, `alwaysOnTop = true`.
- Resizable (no `maximumSize` height lock, unlike `AppMainWindow` which pins height). Since the window is undecorated, resizing is achieved by dragging a bottom-right resize handle rendered in-composable (see below), consistent with how `AppWindow` handles drag-to-move in-composable.
- Minimum size clamped to `200×60` so the window remains usable.
- Movable by dragging anywhere on the window (reuses the `detectDragGestures` + `MouseInfo`/`window.setLocation` pattern from `AppWindow`).
- `visible` bound to `subtitleWindowEnabled` only — **not** bound to `isAppEnabled`.

**Rendering:**
- Observes `appState.nowPlayingText.collectAsState()`.
- **No `MaterialTheme`, `Card`, or other opaque composables** — unlike `AppMainWindow`, which wraps content in a semi-opaque `Card`. The subtitle window must render only text glyphs over a fully transparent background so OBS composites cleanly.
- When text is empty: renders nothing (fully transparent). The window stays present so OBS can keep capturing the region.
- When text is present: centered `Text` with:
  - White fill (`Color.White`), large readable font size.
  - Black outline/shadow via `drawText`-style stroke (Compose `Text` with a shadow or a custom `Modifier.paint`/`drawBehind` stroke). The classic subtitle look: white glyphs with a black halo for legibility over any background.
  - `textAlign = TextAlign.Center`, soft wrap enabled so long sentences split into multiple centered lines that fit the window width. Text that exceeds the window height is clipped at the bottom edge — no ellipsis.
- An in-composable resize handle (bottom-right corner) for sizing, since undecorated windows have no native resize grip. Render a small visible grip so the user can find it.

**Bounds restore and validation:**
- On first composition: restore saved bounds if present and valid; otherwise compute the default position (see below).
- **Valid bounds:** width and height within `200×60` … `screen size`; position such that at least part of the window is visible on the primary screen (guards against off-screen coordinates after monitor layout changes).
- **Invalid or off-screen bounds** → treat as unset and use the default position.

**Default position algorithm** (used when `subtitleWindowX`/`subtitleWindowY` are `null` or saved bounds fail validation):

```
screen = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.bounds
width  = 600
height = 200
margin = 40
x = screen.x + (screen.width - width) / 2
y = screen.y + screen.height - height - margin
```

**Persistence hooks:**
- On window move (drag end) and resize (handle drag end): write the new `x`, `y`, `width`, `height` to `ConfigService.userSettings` and call `saveUserSettings()`.

### `AppMainWindow` (modified)

Currently `AppMainWindow` holds local `nowPlaying` state used only for its own "Now playing: …" status line. The change:

- Accept an `appState: AppState` parameter.
- In `startTTS()`, at the same point the local `nowPlaying = text` assignment runs — **after synthesis completes, immediately before `streamToVirtualAudio`** — also call `appState.updateNowPlaying(text)`. During synthesis (`isLoading == true`) neither the local status line nor the subtitle shows text.
- Call `appState.clearNowPlaying()` in every path that clears local `nowPlaying`:
  - When streaming finishes normally.
  - In `stopTTS()` (which already clears `nowPlaying` synchronously).
  - In a `finally` block (or equivalent) on the playback coroutine so cancellation mid-stream always clears shared state — matching the guarantee `stopTTS()` provides for the local variable but closing the gap if the job is cancelled without going through `stopTTS()`.
- The local `nowPlaying` status line stays as-is. Duplication with `AppState` is intentional: the status line is input-window-scoped; `AppState` is shared across windows.

### `App.kt` (modified)

- Instantiate `val appState = remember { AppState() }`.
- Hold `var subtitleWindowEnabled by remember { mutableStateOf(ConfigService.userSettings.subtitleWindowEnabled) }`.
- Pass `appState` into `AppMainWindow` and `SubtitleWindow`.
- Pass `subtitleWindowEnabled` and an `onSubtitleWindowToggled: (Boolean) -> Unit` callback into `SystemTrays`.
- Compose a new `SubtitleWindow(...)` whose `enabled` is bound to `subtitleWindowEnabled`.

### `ConfigService.UserSettings` (modified)

Add five new fields to the existing `UserSettings` data class (persisted as JSON via Jackson, matching the existing pattern — no separate config object). New fields require KDoc per project conventions (`AGENTS.md`).

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

New fields have Kotlin default values so:
- Existing `settings.json` files without them deserialize cleanly (Jackson fills defaults for missing properties).
- The two explicit `UserSettings(...)` fallback constructors in `loadUserSettings()` (missing file / parse failure) do not need to name the new fields — Kotlin default parameters apply automatically.

`loadUserSettings()` defaults already use `application.conf` for TTS parameters; the new boolean/position fields default inline (not driven by `application.conf`) since they are window-state, not TTS parameters. If a future need arises to externalize defaults, they can move to `application.conf` then.

### `SystemTrays` (modified)

Add a checkbox-style menu item "Show Subtitles" that:
- Accepts `subtitleWindowEnabled: Boolean` from `App.kt` and initializes `CheckboxMenuItem("Show Subtitles", subtitleWindowEnabled)` with that value so the tray reflects persisted config on launch (unlike the existing "Enabled" checkbox, which hardcodes `true` and does not sync with `isAppEnabled`).
- On toggle: updates `ConfigService.userSettings.subtitleWindowEnabled`, calls `saveUserSettings()`, and invokes `onSubtitleWindowToggled(checked)` so `App.kt` updates its `subtitleWindowEnabled` Compose state.

## Data Flow

1. User types text in `AppMainWindow`, presses Enter → `startTTS()`.
2. Synthesis runs on `Dispatchers.IO`; subtitle window stays empty/transparent during this phase.
3. When synthesis completes, `AppMainWindow` sets local `nowPlaying = text` and calls `appState.updateNowPlaying(text)` immediately before `streamToVirtualAudio`.
4. `AppState` emits the text via `StateFlow`.
5. `SubtitleWindow` (collecting the flow) recomposes and renders the wrapped, centered subtitle.
6. When streaming finishes, is cancelled via `stopTTS()`, or the playback coroutine is cancelled, `AppMainWindow` calls `appState.clearNowPlaying()`.
7. `SubtitleWindow` recomposes to empty (transparent).

The subtitle window is purely a reader. All playback control logic stays in `AppMainWindow`.

## Error Handling

The subtitle feature is display-only and degrades gracefully:

- **Empty text** → window renders nothing (transparent). No error state.
- **Window creation failure** → logged via SLF4J (existing logger pattern), window absent; TTS unaffected.
- **Config read/write failure** → handled by existing `ConfigService` error handling; falls back to defaults. Invalid saved bounds (out of range, off-screen, zero/negative dimensions) → treated as unset → default position.
- **Text longer than fits vertically** → wraps horizontally to multiple lines; lines that exceed the window height are clipped at the bottom edge without ellipsis. Resizing reveals more — this is why the window is resizable.

No new error states surface to the user. If subtitles fail, the user simply sees no subtitles; TTS still works.

## Testing Strategy

Following the project's existing test conventions (kotlin.test with JUnit Platform):

- **`AppStateTest`** (new): verify `updateNowPlaying` sets the flow value and `clearNowPlaying` resets it to `""`. Pure unit test, no Compose dependencies. Location: `client/src/test/kotlin/dev/botak/client/AppStateTest.kt` — `client` has a configured test sourceset (`testImplementation(kotlin("test"))`, `tasks.test { useJUnitPlatform() }`).
- **`ConfigServiceTest`** (extended): add cases for the new `UserSettings` fields — defaults on fresh load, round-trip save/load of subtitle bounds, `null` position handling, backward-compatible deserialization of JSON missing the new fields.
- **`SubtitleWindow` composable**: no automated UI test (project has no Compose UI test infrastructure). Verified manually by running `./gradlew :client:run`:
  - Toggle "Show Subtitles" in the tray; confirm checkbox reflects persisted state on relaunch.
  - Type text and confirm subtitle appears with correct styling after synthesis (not during the loading spinner).
  - Confirm subtitle clears when playback ends and when stop is pressed mid-stream.
  - Drag to move and resize via the handle; confirm bounds persist across relaunch.
  - Confirm subtitle window stays visible when "Enabled" is unchecked (input window hidden).
  - In OBS, add a **Window Capture** source targeting the subtitle window and confirm transparent background composites text cleanly over the scene (Game Capture may not capture transparent overlay windows on all setups).
  - This matches how the existing `AppWindow`/`SettingsWindow`/`UpdateWindow` are verified.

## Open Questions for Implementation

- The exact Compose technique for the black text outline (a `Text` shadow vs. a custom `drawText` stroke) is intentionally not pinned here. Either achieves the classic white-glyph-with-black-halo look; pick whichever composes cleanly and reads well against both light and dark OBS backgrounds at implementation time. This is a rendering detail, not an architectural decision.
