# Opaque Subtitle Caption Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the transparent always-on-top subtitle overlay into a decorated, opaque black caption panel that is reliable for OBS Window Capture and easy to find/move on the desktop.

**Architecture:** Keep `AppState` playback text and existing config keys. Rework only presentation and lifecycle: `SubtitleWindow` becomes a normal decorated resizable window with a solid black client area and plain white text; close (X) dismisses like unchecking the tray; `SystemTrays` keeps the "Show Subtitles" checkbox in sync via a held `CheckboxMenuItem` reference updated from Compose when `subtitleWindowEnabled` changes.

**Tech Stack:** Kotlin 2.2, Jetpack Compose Desktop, AWT `SystemTray` / `CheckboxMenuItem`, existing `ConfigService` JSON settings.

**Spec:** `docs/superpowers/specs/2026-07-26-opaque-subtitle-panel-design.md`

---

## File structure

| File | Responsibility |
|------|----------------|
| `client/.../windows/SubtitleWindow.kt` | Opaque decorated window, black panel + white text, bounds init/persist with native chrome, `onDismissed` on close |
| `client/.../windows/SystemTrays.kt` | Tray menu; sync "Show Subtitles" checkbox when Compose `subtitleWindowEnabled` changes (including close-with-X) |
| `client/.../App.kt` | Wire `onDismissed` (persist off + Compose state) into `SubtitleWindow` |

No new modules. No config schema changes. No automated UI tests (manual checklist at end).

**Note — outer vs client size:** With a title bar, AWT `window.width`/`height` include chrome. Existing saved `600×200` sizes yield a slightly smaller client area than the old undecorated window. Do **not** “fix” this; accept and let users resize. Multi-monitor off-primary restore remains the pre-existing primary-screen validation behavior (out of scope).

---

### Task 1: Rework `SubtitleWindow` into an opaque caption panel

**Files:**
- Modify: `client/src/main/kotlin/dev/botak/client/windows/SubtitleWindow.kt` (full file rewrite of the composable surface; keep package, min size constants, bounds helpers with small edits)

- [ ] **Step 1: Replace `SubtitleWindow.kt` with the opaque-panel implementation**

Overwrite `client/src/main/kotlin/dev/botak/client/windows/SubtitleWindow.kt` with:

```kotlin
package dev.botak.client.windows

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import dev.botak.client.AppState
import dev.botak.core.services.ConfigService
import java.awt.GraphicsEnvironment
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.Timer

/** Minimum usable subtitle window outer size, enforced on restore. */
private const val MIN_WIDTH = 200
private const val MIN_HEIGHT = 60

/**
 * Debounce interval (ms) before persisting bounds after a native move/resize burst.
 * Avoids writing settings.json on every pixel of a drag.
 */
private const val BOUNDS_PERSIST_DEBOUNCE_MS = 300

/**
 * Opaque OBS Window Capture caption panel that mirrors the text currently being spoken.
 *
 * Renders a decorated, resizable, non-always-on-top window with a solid black client area and
 * plain white centered subtitle text while audio is playing. When idle the panel stays black and
 * empty so it remains findable on the desktop. Intended as a stable Window Capture source rather
 * than a transparent desktop overlay (layered transparent windows often capture as solid black
 * without usable alpha on Windows).
 *
 * Visibility is bound to [enabled] only — the "Show Subtitles" tray toggle — and is independent of
 * the app "Enabled" toggle that hides [AppMainWindow]. Closing the window via the title-bar close
 * control calls [onDismissed] so the parent can persist off and uncheck the tray item.
 *
 * Bounds are restored from [ConfigService.userSettings] on first composition and debounced-persisted
 * after native move/resize. Saved width/height are outer window sizes (including title bar chrome).
 *
 * @param enabled Whether the subtitle window is shown (the persisted "Show Subtitles" tray state).
 * @param appState Shared UI state providing the now-playing text.
 * @param onDismissed Called when the user closes the window (X); parent must set enabled false and persist.
 */
@Composable
fun SubtitleWindow(
    enabled: Boolean,
    appState: AppState,
    onDismissed: () -> Unit,
) {
    val text by appState.nowPlayingText.collectAsState()

    Window(
        onCloseRequest = onDismissed,
        title = "Botak TTS Subtitles",
        transparent = false,
        undecorated = false,
        alwaysOnTop = false,
        resizable = true,
        visible = enabled,
    ) {
        LaunchedEffect(Unit) {
            initSubtitleBounds(window)
        }
        DisposableEffect(window) {
            val persistTimer =
                Timer(BOUNDS_PERSIST_DEBOUNCE_MS) {
                    persistSubtitleBounds(window)
                }.apply { isRepeats = false }

            val componentListener =
                object : ComponentAdapter() {
                    override fun componentMoved(e: ComponentEvent?) {
                        persistTimer.restart()
                    }

                    override fun componentResized(e: ComponentEvent?) {
                        persistTimer.restart()
                    }
                }

            window.addComponentListener(componentListener)
            onDispose {
                persistTimer.stop()
                window.removeComponentListener(componentListener)
            }
        }
        SubtitleContent(text = text)
    }
}

/**
 * Full-size black caption surface. Renders centered white subtitle text when [text] is non-blank;
 * otherwise only the black background (idle / findable panel).
 *
 * @param text The current now-playing text.
 */
@Composable
private fun SubtitleContent(text: String) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (text.isNotBlank()) {
            SubtitleText(text)
        }
    }
}

/**
 * Renders the subtitle as plain white bold centered text, soft-wrapped to the window width.
 *
 * @param text The text to render.
 */
@Composable
private fun SubtitleText(text: String) {
    BasicText(
        text = text,
        style =
            TextStyle(
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
        softWrap = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Restores the subtitle window bounds from [ConfigService.userSettings], validating them against
 * the primary screen. Saved width/height are clamped to `>= [MIN_WIDTH]`/`[MIN_HEIGHT]` and the
 * screen size. If the saved position is `null` or places the window fully off-screen, a default
 * position (horizontally centered, near the bottom with a 40px margin) is computed using the
 * resolved width/height.
 *
 * Note: with native chrome, width/height are outer sizes (title bar included). Multi-monitor
 * positions fully off the primary screen still fall back to the default (pre-existing behavior).
 *
 * @param window The underlying [ComposeWindow] to position and size.
 */
private fun initSubtitleBounds(window: ComposeWindow) {
    val screen =
        GraphicsEnvironment.getLocalGraphicsEnvironment()
            .defaultScreenDevice.defaultConfiguration.bounds
    val settings = ConfigService.userSettings
    val width = settings.subtitleWindowWidth.coerceIn(MIN_WIDTH, screen.width)
    val height = settings.subtitleWindowHeight.coerceIn(MIN_HEIGHT, screen.height)

    val savedX = settings.subtitleWindowX
    val savedY = settings.subtitleWindowY
    // Require at least 20px of the window to overlap the primary screen on every edge check.
    val onScreen =
        savedX != null && savedY != null &&
            savedX < screen.x + screen.width - 20 &&
            savedY < screen.y + screen.height - 20 &&
            savedX + width > screen.x + 20 &&
            savedY + height > screen.y + 20

    val x: Int
    val y: Int
    if (onScreen) {
        x = savedX!!
        y = savedY!!
    } else {
        val margin = 40
        x = screen.x + (screen.width - width) / 2
        y = screen.y + screen.height - height - margin
    }
    window.setBounds(x, y, width, height)
}

/**
 * Persists the current window outer position and size to [ConfigService.userSettings] and saves.
 *
 * @param window The underlying [ComposeWindow] whose bounds to persist.
 */
private fun persistSubtitleBounds(window: ComposeWindow) {
    ConfigService.userSettings.subtitleWindowX = window.x
    ConfigService.userSettings.subtitleWindowY = window.y
    ConfigService.userSettings.subtitleWindowWidth = window.width
    ConfigService.userSettings.subtitleWindowHeight = window.height
    ConfigService.saveUserSettings()
}
```

**Implementation notes for the agent:**
- If `javax.swing.Timer` is undesirable in this module, an equivalent debounce with `kotlinx.coroutines` (`LaunchedEffect` + delay restarted on move/resize via a `mutableStateOf` tick) is fine; the requirement is **debounced persist after native move/resize**, not the timer type.
- Remove all outline-offset text, drag-to-move, and resize-grip code from the old file.
- `onCloseRequest = onDismissed` — do **not** call `exitApplication`.

- [ ] **Step 2: Fix compile break in `App.kt` temporarily if needed**

`SubtitleWindow` now requires `onDismissed`. If you compile mid-task, add a stub in `App.kt`:

```kotlin
SubtitleWindow(
    enabled = subtitleWindowEnabled,
    appState = appState,
    onDismissed = {
        ConfigService.userSettings.subtitleWindowEnabled = false
        ConfigService.saveUserSettings()
        subtitleWindowEnabled = false
    },
)
```

(Full tray sync is Task 2–3; this stub is enough for Task 1 to compile.)

- [ ] **Step 3: Build the client module**

Run:

```bash
./gradlew :client:compileKotlin
```

Expected: `BUILD SUCCESSFUL` (no unresolved `onDismissed`, no missing imports).

- [ ] **Step 4: Commit**

```bash
git add client/src/main/kotlin/dev/botak/client/windows/SubtitleWindow.kt client/src/main/kotlin/dev/botak/client/App.kt
git commit -m "feat: opaque decorated subtitle caption panel for OBS"
```

---

### Task 2: Sync tray "Show Subtitles" checkbox with Compose state

**Files:**
- Modify: `client/src/main/kotlin/dev/botak/client/windows/SystemTrays.kt`

**Approach (pinned):** Keep a `CheckboxMenuItem` reference created in `useSystemTray`. Drive checkbox state from Compose with `LaunchedEffect(subtitleWindowEnabled)` so close-with-X (Compose → false) unchecks the tray. Make the item listener **idempotent**: if the new state already matches what Compose would set, still fine to call the callback, but avoid double-persist loops by only updating `subtitleItem.state` when it differs from `subtitleWindowEnabled`.

- [ ] **Step 1: Hold the subtitle menu item across composition**

Refactor `SystemTrays` so the tray is set up once and the subtitle checkbox can be updated when `subtitleWindowEnabled` changes.

Replace the body of `SystemTrays` and `useSystemTray` as follows (preserve Settings / updates / Enabled / Exit behavior; only subtitle sync is new):

```kotlin
@Composable
@Preview
fun SystemTrays(
    onAppEnabled: () -> Unit,
    onAppDisabled: () -> Unit,
    subtitleWindowEnabled: Boolean,
    onSubtitleWindowToggled: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    exitApplication: () -> Unit,
    ttsService: TTSService,
    audioStreamService: AudioStreamService,
) {
    var showSettings by remember { mutableStateOf(false) }
    // Held so LaunchedEffect can sync checkbox when the subtitle window is closed via X.
    val subtitleMenuItem = remember { arrayOfNulls<CheckboxMenuItem>(1) }

    LaunchedEffect(Unit) {
        if (SystemTray.isSupported()) {
            LOGGER.debug("System tray supported. Use system tray")
            useSystemTray(
                onSettingsItem = { showSettings = true },
                onCheckForUpdatesItem = onCheckForUpdates,
                onExitItem = { exitApplication() },
                onAppEnabled = onAppEnabled,
                onAppDisabled = onAppDisabled,
                subtitleWindowEnabled = subtitleWindowEnabled,
                onSubtitleWindowToggled = onSubtitleWindowToggled,
                subtitleMenuItemOut = subtitleMenuItem,
            )
        }
    }

    // Keep AWT checkbox aligned with Compose (tray click or window close).
    LaunchedEffect(subtitleWindowEnabled) {
        val item = subtitleMenuItem[0] ?: return@LaunchedEffect
        if (item.state != subtitleWindowEnabled) {
            item.state = subtitleWindowEnabled
        }
    }

    SettingsWindow(
        ttsService = ttsService,
        audioStreamService = audioStreamService,
        visible = showSettings,
        onClose = { showSettings = false },
    )
}
```

Update KDoc on `SystemTrays` to mention that the "Show Subtitles" checkbox tracks [subtitleWindowEnabled] even when the window is closed from its title bar.

- [ ] **Step 2: Plumb `subtitleMenuItemOut` into `useSystemTray`**

Change `useSystemTray` signature and subtitle item setup:

```kotlin
private fun useSystemTray(
    onSettingsItem: () -> Unit,
    onCheckForUpdatesItem: () -> Unit,
    onExitItem: () -> Unit,
    onAppEnabled: () -> Unit,
    onAppDisabled: () -> Unit,
    subtitleWindowEnabled: Boolean,
    onSubtitleWindowToggled: (Boolean) -> Unit,
    subtitleMenuItemOut: Array<CheckboxMenuItem?>,
) {
    // ... existing tray/image/popup setup through enabledItem ...

    val subtitleItem = CheckboxMenuItem("Show Subtitles", subtitleWindowEnabled)
    subtitleItem.addItemListener {
        val checked = subtitleItem.state
        LOGGER.debug("Show Subtitles toggle: $checked")
        ConfigService.userSettings.subtitleWindowEnabled = checked
        ConfigService.saveUserSettings()
        onSubtitleWindowToggled(checked)
    }
    popup.add(subtitleItem)
    subtitleMenuItemOut[0] = subtitleItem

    // ... exitItem, tray.add, etc. unchanged ...
}
```

Update `useSystemTray` KDoc `@param` list for `subtitleMenuItemOut`: *Output slot set to the live "Show Subtitles" checkbox so Compose can sync its state.*

**Why `Array<CheckboxMenuItem?>`:** `remember { }` needs a stable mutable holder; a one-element array is a simple pattern without introducing a new type. Alternatives (`AtomicReference`, custom holder class) are fine if preferred — same behavior.

**Reentrancy:** Setting `item.state = false` from `LaunchedEffect` fires `ItemListener` on some JDKs. That re-enters `onSubtitleWindowToggled(false)` and re-saves config — acceptable if idempotent. If you observe a loop or flicker, guard the listener:

```kotlin
subtitleItem.addItemListener {
    val checked = subtitleItem.state
    // Skip work if App.kt state already matches (programmatic sync).
    // Parent still owns Compose state; this only avoids redundant save when already false/true.
    ConfigService.userSettings.subtitleWindowEnabled = checked
    ConfigService.saveUserSettings()
    onSubtitleWindowToggled(checked)
}
```

Idempotent parent updates (`subtitleWindowEnabled = checked` when already equal) are enough; do not add complex locks unless a real loop appears.

- [ ] **Step 3: Compile**

Run:

```bash
./gradlew :client:compileKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add client/src/main/kotlin/dev/botak/client/windows/SystemTrays.kt
git commit -m "fix: sync Show Subtitles tray checkbox with window state"
```

---

### Task 3: Wire dismiss in `App.kt` and verify end-to-end

**Files:**
- Modify: `client/src/main/kotlin/dev/botak/client/App.kt` (ensure `onDismissed` is complete; import `ConfigService` if not already present — it already is)

- [ ] **Step 1: Confirm `SubtitleWindow` call site**

In `App.kt` inside `application { }`, the subtitle block must be:

```kotlin
SubtitleWindow(
    enabled = subtitleWindowEnabled,
    appState = appState,
    onDismissed = {
        ConfigService.userSettings.subtitleWindowEnabled = false
        ConfigService.saveUserSettings()
        subtitleWindowEnabled = false
    },
)
```

`SystemTrays` still receives:

```kotlin
subtitleWindowEnabled = subtitleWindowEnabled,
onSubtitleWindowToggled = { subtitleWindowEnabled = it },
```

Tray path already persists inside `useSystemTray`; close path persists in `onDismissed`. Do not remove tray-side persist (both paths should save).

- [ ] **Step 2: Full client compile + unit tests (config still green)**

Run:

```bash
./gradlew :client:compileKotlin :core:test
```

Expected: `BUILD SUCCESSFUL`; existing `ConfigServiceTest` subtitle cases still pass (no schema change).

- [ ] **Step 3: Manual smoke (`./gradlew :client:run`)**

Checklist (from spec):

1. Tray **Show Subtitles** → decorated window, solid black client, native title bar, **not** always-on-top.
2. Idle = black only; speak via main window → white centered text after synthesis; end → black again.
3. Move via title bar, resize via edges; quit and relaunch → bounds roughly restored (outer size includes chrome).
4. Close with **X** → window hides, tray **unchecked**, relaunch stays hidden.
5. Toggle tray on/off after close-with-X → checkbox and visibility stay aligned.
6. (Optional) OBS Window Capture “Botak TTS Subtitles” → black + white text while speaking.

- [ ] **Step 4: Commit any remaining wiring tweaks**

If App.kt was already committed in Task 1 with the stub, and nothing changed, skip. Otherwise:

```bash
git add client/src/main/kotlin/dev/botak/client/App.kt
git commit -m "feat: dismiss subtitle panel persists off and hides window"
```

- [ ] **Step 5: Final commit only if docs/comments left**

No requirement to edit the 2026-06-29 design doc in this plan; the 2026-07-26 spec is source of truth. Optional one-line pointer in the old design status is YAGNI unless the user asks.

---

## Execution handoff

After this plan is approved by the plan reviewer and the user picks an execution mode:

1. **Subagent-Driven (recommended)** — `superpowers:subagent-driven-development`: fresh subagent per task, review between tasks.
2. **Inline Execution** — `superpowers:executing-plans`: batch steps in-session with checkpoints.

Do not start implementation in the planning session unless the user explicitly chooses an option above.
