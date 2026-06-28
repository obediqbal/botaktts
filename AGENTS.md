# AGENTS.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

BotakTTS is a Kotlin desktop application that converts text to speech via Google Cloud TTS API and streams it to a virtual microphone for use in voice chat applications (Discord, TeamSpeak, in-game chat). Built with Jetpack Compose Desktop.

## Build Commands

```bash
./gradlew build                      # Build the project
./gradlew test                       # Run all tests
./gradlew :client:run                # Run the desktop GUI application
./gradlew :core:run                  # Run the core CLI (interactive mode)
./gradlew :client:createDistributable # Create distributable app
./gradlew :client:createInstaller    # Create NSIS installer (requires NSIS)
./gradlew :client:createPortable     # Create portable executable
./gradlew ktlintCheck                # Lint check
./gradlew ktlintFormat               # Auto-format code
```

## Architecture

**Multi-module Gradle project with two modules:**

### Core Module (`core/`)
Business logic and services:
- **TTSService**: Google Cloud TTS integration, voice/language selection, pitch/speed adjustment
- **AudioStreamService**: Streams PCM audio to virtual audio devices using Java Sound API
- **ConfigService**: User settings persistence (JSON) in platform-specific app data directories
- **CredentialsService**: OAuth token management via remote token issuer function

### Client Module (`client/`)
Desktop GUI using Compose Desktop:
- **AppWindow**: Floating, always-on-top, transparent input window. Enter to speak, Ctrl+Shift+H to toggle visibility
- **SettingsWindow**: Language/voice/speed/pitch/volume controls with preview
- **SystemTrays**: System tray icon with settings/enable/exit menu
- **GlobalHotKeyListener**: Global Ctrl+Shift+H hotkey via JNativeHook
- **Utils**: Windows-specific focus forcing via JNA/Win32

## Data Flow

1. User types text → AppWindow
2. Text → TTSService.synthesizeSpeech() → Google Cloud TTS API
3. Raw PCM audio (24kHz LINEAR16) returned
4. AudioStreamService streams to "CABLE Input (VB-Audio Virtual Cable)"
5. Voice chat app receives TTS as microphone input

## Key Technologies

- Kotlin 2.2.0, JVM 17
- Jetpack Compose Desktop 1.8.0
- Google Cloud Text-to-Speech API
- JNativeHook (global hotkeys)
- JNA (Windows native interop)
- NSIS for Windows installers

## Configuration

User settings stored in platform-specific locations:
- Windows: `%LOCALAPPDATA%\Botak TTS\`
- macOS: `~/Library/Application Support/Botak TTS/`
- Linux: `~/.config/botak-tts/`

Default config in `core/src/main/resources/application.conf`.

## Platform Notes

- Windows focus forcing uses Win32 API (Utils.kt) - not portable
- Virtual audio cable (VB-Audio Cable) required for virtual microphone functionality
- Token issuer function: `https://us-central1-tts-botak.cloudfunctions.net/token-issuer-function`

## Documentation

- **Always write KDoc for Kotlin code.** Every module, class, object, interface, enum, and public function/method/property in `core/` and `client/` must have a `/** */` KDoc comment describing its purpose and behavior.
- Document parameters (`@param`), return values (`@return`), and thrown exceptions (`@throws`) where they add value. Include valid ranges/constraints for values that are validated or persisted.
- File-level KDoc is encouraged for files whose top-level declarations benefit from broader context.
- Keep comments up to date when changing code; do not leave stale KDoc.

## Cursor Cloud specific instructions

This Linux VM is provisioned by the startup update script (`chmod +x gradlew` + `./gradlew --quiet help`). System dependencies (JDK 17, `libxkbcommon-x11-0`) are baked into the VM snapshot, not the update script. Standard build/lint/test/run commands are in the **Build Commands** section above.

- **Toolchain:** Build requires a **JDK 17** toolchain (`jvmToolchain(17)`); the Gradle 8.5 wrapper itself runs fine on the VM's default JDK 21. `gradlew` is committed non-executable (`100644`), so it must be `chmod +x`'d (the update script handles this).
- **No credentials needed for TTS:** auth goes through the public token-issuer Cloud Function (see Platform Notes URL); there is no service-account key or secret to set. The real Google Cloud TTS API is reachable from the VM, so synthesis works end-to-end.
- **GUI runs headless on `DISPLAY=:1`:** `./gradlew :client:run` launches the Compose window there. Skiko logs `RenderException: Cannot create Linux GL context` and falls back to software rendering — this warning is benign and the window still renders.
- **Virtual-mic playback is Windows-only:** the GUI's "speak" action and the CLI `SYNTH` command call `streamToVirtualAudio`, which looks for a mixer named `CABLE Input (VB-Audio Virtual Cable)`. That device cannot exist on Linux, so it throws `IllegalStateException("Virtual Audio not found")`. In the **GUI** this Error is uncaught and **terminates the app** after one submit; the speak-to-mic path therefore cannot be exercised end-to-end on Linux. To verify the core text→speech pipeline without the playback crash, use the `:core:run` interactive CLI (`SYNTH`/`GETVOICENAMES` commands) and confirm the `SynthesizeSpeech` gRPC call returns and `synthesized speech` is logged. There is also no audio output device, so the Settings preview (`streamToSpeakers`) won't play either.
- **Native-lib gotcha:** JNativeHook (global hotkey) loads a native `.so` needing `libxkbcommon-x11.so.0`. A missing native lib raises `UnsatisfiedLinkError` (an `Error`, not `Exception`), which the app's `try/catch (Exception)` does **not** catch and which crashes the GUI — keep that lib present.
- **Lint/build caveat:** there is a pre-existing ktlint violation (`client/build.gradle.kts` trailing blank line) on `main`, so `./gradlew ktlintCheck` and the full `./gradlew build` fail on it. Build/verify with `./gradlew build -x ktlintKotlinScriptCheck` (or run `./gradlew ktlintFormat` if you intend to fix it).
