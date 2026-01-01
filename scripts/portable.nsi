;--------------------------------
; BotakTTS Portable Script
; Self-extracting executable - runs immediately without installation
;--------------------------------

;--------------------------------
; General Settings
;--------------------------------
Name "BotakTTS Portable"
OutFile "BotakTTS-${VERSION}-Portable.exe"
Unicode True
RequestExecutionLevel user
SilentInstall silent
AutoCloseWindow true

;--------------------------------
; Portable Section
;--------------------------------
Section "Portable"
    ; Extract to temporary plugin directory (auto-cleaned by NSIS)
    InitPluginsDir
    SetOutPath "$PLUGINSDIR\BotakTTS"
    
    ; Extract all application files
    File /r "${APP_DIR}\*.*"
    
    ; Run the application and wait for it to close
    ExecWait '"$PLUGINSDIR\BotakTTS\BotakTTSClient.exe"'
    
    ; Cleanup is automatic when using $PLUGINSDIR
SectionEnd
