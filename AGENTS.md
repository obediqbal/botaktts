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
