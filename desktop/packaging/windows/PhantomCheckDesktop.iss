; #470 PhantomCheck Desktop Inno Setup Installer Script
; Packages the jpackage app-image into a user-friendly per-user Windows setup.exe
; with Desktop shortcut checkbox (checked by default) and "Launch after install" checkbox (checked by default).

#ifndef MyAppVersion
  #define MyAppVersion "0.5.0"
#endif

#define MyAppName "PhantomCheck Desktop"
#define MyAppPublisher "PhantomCheck"
#define MyAppExeName "PhantomCheckDesktop.exe"
#define AppImageDir "..\..\build\compose\binaries\main\app\PhantomCheckDesktop"

[Setup]
AppId={{9B7E3C4A-6F1D-4E2A-9D8C-5A1B2C3D4E5F}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={localappdata}\PhantomCheckDesktop
DefaultGroupName=PhantomCheck
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
OutputDir=..\..\build\compose\binaries\main\inno
OutputBaseFilename=PhantomCheckDesktop-{#MyAppVersion}-setup
SetupIconFile=..\..\src\main\resources\icon.ico
UninstallDisplayIcon={app}\{#MyAppExeName}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
CloseApplications=yes
RestartApplications=no

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"
Name: "firewall"; Description: "Add Windows Firewall exception for LAN link (requires admin)"; GroupDescription: "Network"; Flags: unchecked

[Files]
Source: "{#AppImageDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\PhantomCheck\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Registry]
; Ensure any user-enabled HKCU Run autostart key is cleanly removed on uninstall
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: none; ValueName: "PhantomCheckDesktop"; Flags: uninsdeletevalue

[Run]
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall add rule name=""PhantomCheck Desktop"" dir=in action=allow program=""{app}\{#MyAppExeName}"" enable=yes profile=private"; Flags: runhidden waituntilterminated; Tasks: firewall
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

[UninstallRun]
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall delete rule name=""PhantomCheck Desktop"""; Flags: runhidden waituntilterminated; RunOnceId: "RemoveFirewallRule"
