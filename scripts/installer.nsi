;--------------------------------
; BotakTTS Installer Script
; Modern UI installer with shortcuts and uninstaller
;--------------------------------

!include "MUI2.nsh"

;--------------------------------
; General Settings
;--------------------------------
Name "BotakTTS"
OutFile "BotakTTS-${VERSION}-Setup.exe"
Unicode True
InstallDir "$PROGRAMFILES64\BotakTTS"
InstallDirRegKey HKLM "Software\BotakTTS" "InstallDir"
RequestExecutionLevel admin

;--------------------------------
; Interface Settings
;--------------------------------
!define MUI_ABORTWARNING
!define MUI_ICON "${NSISDIR}\Contrib\Graphics\Icons\modern-install.ico"
!define MUI_UNICON "${NSISDIR}\Contrib\Graphics\Icons\modern-uninstall.ico"

;--------------------------------
; Pages
;--------------------------------
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

;--------------------------------
; Languages
;--------------------------------
!insertmacro MUI_LANGUAGE "English"

;--------------------------------
; Installer Section
;--------------------------------
Section "Install"
    SetOutPath "$INSTDIR"

    ; In a silent update the old app may still hold its .exe open. Wait for it to release the
    ; lock before overwriting files (up to 10s). If the file remains locked, File /r fails and
    ; NSIS falls back to reboot-required.
    IfSilent 0 skip_wait
    StrCpy $1 0
    retry_loop:
        ClearErrors
        FileOpen $0 "$INSTDIR\BotakTTSClient.exe" a
        IfErrors still_locked file_unlocked
    still_locked:
        Sleep 500
        IntOp $1 $1 + 1
        IntCmp $1 20 give_up give_up retry_loop
    give_up:
        ; Proceed anyway; File /r will trigger reboot-required if still locked
        Goto skip_wait
    file_unlocked:
        FileClose $0
    skip_wait:

    ; Copy all application files
    File /r "${APP_DIR}\*.*"
    
    ; Create uninstaller
    WriteUninstaller "$INSTDIR\Uninstall.exe"
    
    ; Registry keys for Add/Remove Programs
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\BotakTTS" "DisplayName" "BotakTTS"
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\BotakTTS" "UninstallString" '"$INSTDIR\Uninstall.exe"'
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\BotakTTS" "InstallLocation" "$INSTDIR"
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\BotakTTS" "DisplayVersion" "${VERSION}"
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\BotakTTS" "Publisher" "Botak"
    WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\BotakTTS" "NoModify" 1
    WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\BotakTTS" "NoRepair" 1
    
    ; Save install location
    WriteRegStr HKLM "Software\BotakTTS" "InstallDir" "$INSTDIR"
    
    ; Create Start Menu shortcuts
    CreateDirectory "$SMPROGRAMS\BotakTTS"
    CreateShortcut "$SMPROGRAMS\BotakTTS\BotakTTS.lnk" "$INSTDIR\BotakTTSClient.exe"
    CreateShortcut "$SMPROGRAMS\BotakTTS\Uninstall.lnk" "$INSTDIR\Uninstall.exe"
    
    ; Create Desktop shortcut
    CreateShortcut "$DESKTOP\BotakTTS.lnk" "$INSTDIR\BotakTTSClient.exe"

    ; In a silent update, relaunch the app de-elevated so it does not inherit the installer's
    ; admin rights. explorer.exe runs at the logged-in user's medium integrity, so the launched
    ; app starts without elevation. Needs no NSIS plugin (no CI change).
    IfSilent 0 +2
    Exec '"$WINDIR\explorer.exe" "$INSTDIR\BotakTTSClient.exe"'
SectionEnd

;--------------------------------
; Uninstaller Section
;--------------------------------
Section "Uninstall"
    ; Remove files and directories
    RMDir /r "$INSTDIR"
    
    ; Remove Start Menu shortcuts
    RMDir /r "$SMPROGRAMS\BotakTTS"
    
    ; Remove Desktop shortcut
    Delete "$DESKTOP\BotakTTS.lnk"
    
    ; Remove registry keys
    DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\BotakTTS"
    DeleteRegKey HKLM "Software\BotakTTS"
SectionEnd
