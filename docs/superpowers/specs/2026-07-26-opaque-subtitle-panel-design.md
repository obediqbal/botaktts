# Opaque Subtitle Caption Panel — Design

**Date:** 2026-07-26  
**Status:** Approved for planning  
**Supersedes (OBS presentation):** the transparent-overlay assumptions in `2026-06-29-subtitle-window-design.md` for how the subtitle window looks and is captured. Playback sync, tray toggle, config field names, and `AppState` wiring from that design remain in force unless this document changes them.

## Goal

Change the existing subtitle window from a transparent, always-on-top desktop overlay into a **normal opaque caption panel** intended as an OBS **Window Capture** source. Streamers get reliable black background + white text in OBS, and a discoverable window on the desktop when nothing is speaking.

## Problem

The current `SubtitleWindow` (`transparent = true`, `undecorated = true`, `alwaysOnTop = true`) was designed so OBS would composite only the glyphs. On Windows, layered/transparent Java and Compose windows typically **do not** capture with alpha in Window Capture: the source often appears as a **solid black rectangle**, and when idle the desktop window is effectively invisible, so users cannot find or move it.

Separately, streamers using Window Capture do not need a always-on-top ghost overlay on the gaming desktop; they need a stable, capturable window they can park on a second monitor or under other apps.

## Requirements

The subtitle window must:

- **Be opaque** — solid black client area at all times (including when no text is showing).
- **Use native window chrome** — decorated, resizable title-bar window (standard drag and resize edges).
- **Not be always-on-top** — normal Z-order so it can sit behind other windows or on another display; OBS Window Capture still works.
- **Show plain white subtitle text** when audio is playing — bold, centered, soft-wrapped; no black outline stack.
- **Stay black and empty when idle** — no placeholder copy; the black panel itself is the findability cue.
- **Remain tray-toggleable** — “Show Subtitles” still shows/hides the window independently of the app “Enabled” toggle.
- **Treat close (X) as hide** — same as unchecking “Show Subtitles”: persist off and uncheck the tray item.
- **Persist bounds** — position and size still saved via existing `UserSettings` fields after move/resize with native chrome.
- **Keep playback sync** — text still appears only while audio is actively streaming (after synthesis); cleared on end or cancel (unchanged `AppState` / `AppMainWindow` writer behavior).

## Non-Goals (YAGNI)

- Chroma-key / green-screen background mode.
- Browser Source / local HTML caption path.
- Font, color, or size customization UI.
- Auto show/hide tied to audio start/stop.
- In-app cropping of the title bar for capture (users may crop in OBS).
- OS “exclude from capture” / hide-from-stream flags.
- Hotkey for the subtitle window.
- Automated Compose UI tests (none exist for other windows; verify manually).

## Architecture

No new modules or shared-state types. This is a **presentation and window-lifecycle** change on top of the existing subtitle feature:

1. **`SubtitleWindow`** — rework window flags, content paint, remove custom drag/resize chrome; add dismiss callback.
2. **`App.kt`** — wire `onDismissed` so close turns subtitles off and persists.
3. **`SystemTrays`** — keep the tray checkbox in sync when the window is closed from the title bar (not only when the user clicks the menu item).

`AppState`, TTS/audio pipeline, and config field *names* stay as-is.

```
[AppMainWindow] --updateNowPlaying/clear--> [AppState.nowPlayingText]
                                                    |
                                                    v
[SystemTrays "Show Subtitles"] <--> [App.kt subtitleWindowEnabled] <--> [SubtitleWindow visible]
         ^                                      ^
         |                                      |
         +-------- checkbox state sync ---------+  (close X and tray both drive enabled=false)
```

## Components

### `SubtitleWindow` (modify)

**File:** `client/src/main/kotlin/dev/botak/client/windows/SubtitleWindow.kt`

**Window properties (new):**

| Property | Value |
|----------|--------|
| `transparent` | `false` |
| `undecorated` | `false` |
| `alwaysOnTop` | `false` |
| `resizable` | `true` |
| `visible` | bound to `enabled` only (not app Enabled) |
| `title` | `"Botak TTS Subtitles"` (OBS source name) |

**API:**

```kotlin
@Composable
fun SubtitleWindow(
    enabled: Boolean,
    appState: AppState,
    onDismissed: () -> Unit,
)
```

- `onCloseRequest` invokes `onDismissed()` (parent persists off and sets enabled false). Do not call `exitApplication`.
- Remove full-window `detectDragGestures` move logic and the bottom-right custom resize grip.
- Content: full-size black `Box`; when `nowPlayingText` is non-blank, centered white `BasicText`/`Text` (bold ~36.sp, center align, soft wrap, clip overflow — no ellipsis, no eight-offset outline).

**Bounds:**

- Keep `initSubtitleBounds` / `persistSubtitleBounds` and min size `200×60`.
- Because native chrome replaces custom drag handlers, attach a listener (e.g. AWT `ComponentListener` on move/resize, or equivalent Compose-window hook) so position and size still persist when the user finishes moving or resizing via the title bar and edges. Persist on interaction end (or debounced equivalent), not on every pixel of drag if that would thrash disk — matching the previous “persist on drag end” intent is enough.

### `App.kt` (modify)

- Pass `onDismissed` into `SubtitleWindow` that:
  1. Sets `ConfigService.userSettings.subtitleWindowEnabled = false`
  2. Calls `ConfigService.saveUserSettings()`
  3. Sets Compose `subtitleWindowEnabled = false`
- Ensure the tray checkbox reflects that change (see SystemTrays).

### `SystemTrays` (modify)

- Keep “Show Subtitles” `CheckboxMenuItem` behavior: user toggle still persists and calls `onSubtitleWindowToggled`.
- **Required:** when `subtitleWindowEnabled` becomes `false` because the window was closed (or any non-menu path), set `subtitleItem.state = false` on the EDT without fighting the listener (idempotent listener or ignore programmatic updates).
- Implementation may hold a reference to `subtitleItem` and expose a small sync path from `App.kt` (e.g. side effect when state changes, or a setter callback registered at tray setup). Prefer the smallest change that keeps checkbox and window visibility consistent after close-with-X and after relaunch.

### Config (no schema change)

Existing fields only:

- `subtitleWindowEnabled`
- `subtitleWindowX` / `subtitleWindowY`
- `subtitleWindowWidth` / `subtitleWindowHeight`

No new keys. Product meaning of “enabled” is now “opaque caption panel shown,” not “transparent overlay shown.”

## Data flow

Unchanged for text:

1. User speaks via main window → synthesis → before stream, `appState.updateNowPlaying(text)`.
2. `SubtitleWindow` collects flow and paints white text on black.
3. Stream end / cancel → `clearNowPlaying()` → black empty panel (window still visible if enabled).

Visibility:

1. Tray check → enabled true → window shown.
2. Tray uncheck or window X → enabled false, persisted → window hidden; tray unchecked.

## Error handling

- Same graceful degradation as today: if text state fails, panel is simply empty black; TTS still works.
- Close and tray paths must not leave tray checked while window hidden (or the reverse).

## Testing

**Automated:** no new unit tests required beyond existing config coverage (fields unchanged). If tray sync is extracted into a tiny pure helper, optional unit test; not required if sync stays in AWT wiring.

**Manual** (`./gradlew :client:run`):

1. Enable “Show Subtitles” → decorated window, solid black client area, native title bar.
2. Idle remains black; after TTS playback starts, white centered text; after end, black again.
3. Move via title bar and resize via edges; quit and relaunch → bounds restore.
4. Close with X → window hides, tray unchecked, relaunch stays hidden.
5. Tray toggle on/off stays consistent with checkbox after close-with-X.
6. Window is not always-on-top (can be covered by other apps).
7. OBS Window Capture of “Botak TTS Subtitles” shows black background and white text while speaking (not a failed transparent capture). Title bar may appear in the source; cropping in OBS is acceptable.

## Relationship to prior design

| Topic | 2026-06-29 design | This design |
|-------|-------------------|-------------|
| Background | Fully transparent | Opaque black |
| Chrome | Undecorated + custom grip | Native decorated + resizable |
| Z-order | Always on top | Normal |
| Text style | White + black outline | White only |
| Idle | Invisible empty region | Visible black panel |
| Close (X) | No-op | Hide + persist off + tray sync |
| OBS expectation | Alpha composite | Opaque Window Capture source |
| AppState / tray name / config keys | — | Unchanged |

## Implementation notes (non-normative)

- Prefer reusing patterns from `SettingsWindow` / `UpdateWindow` for decorated, non-transparent windows where applicable.
- KDoc on `SubtitleWindow` must describe an OBS Window Capture caption panel, not a transparent overlay.
- Update or supersede comments in older subtitle docs only as needed during implementation planning; this file is the source of truth for the new presentation.
