; #470 / #495 / #496 PhantomCheck Desktop Inno Setup Installer Script
; Packages the jpackage app-image into a user-friendly per-user Windows setup.exe
; Sole Windows update channel (legacy jpackage MSI retired in #496; PrepareToInstall auto-cleans legacy MSI/Inno).

#ifndef MyAppVersion
  #define MyAppVersion "0.9.0"
#endif

#define MyAppName "PhantomCheck Desktop"
#define MyAppPublisher "PhantomCheck"
#define MyAppExeName "PhantomCheckDesktop.exe"
#define MyAppIdGuid "{9B7E3C4A-6F1D-4E2A-9D8C-5A1B2C3D4E5F}"
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
CloseApplicationsFilter=*.exe
RestartApplications=no
SetupLogging=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Messages]
; #496 Bilingual Restart Manager fallback prompt (rarely seen after graceful shutdown)
ApplicationsFound=PhantomCheck Desktop is currently running. Setup can close it automatically to complete the upgrade (your settings and pairings in %%APPDATA%% are preserved).%n%n偵測到 PhantomCheck 桌面端正在執行，安裝程式將自動關閉以完成升級（設定與配對資料皆會保留）。
CloseApplications=&Automatically close the applications (自動關閉應用程式)

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"
Name: "firewall"; Description: "Add Windows Firewall exception for LAN link (requires admin)"; GroupDescription: "Network"; Flags: unchecked

[Files]
Source: "{#AppImageDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\PhantomCheck\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[InstallDelete]
; #495 / #496 Remove stale IPC port file only; preserve %APPDATA%\PhantomCheckDesktop settings & pairings
Type: files; Name: "{userappdata}\PhantomCheckDesktop\instance.port"

[Registry]
; #495 Ensure any user-enabled HKCU Run autostart key is cleanly removed on uninstall
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: none; ValueName: "PhantomCheckDesktop"; Flags: uninsdeletevalue

[Run]
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall add rule name=""PhantomCheck Desktop (App)"" dir=in action=allow program=""{app}\{#MyAppExeName}"" enable=yes profile=any"; Flags: runhidden waituntilterminated; Tasks: firewall
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

[UninstallRun]
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall delete rule name=""PhantomCheck Desktop"""; Flags: runhidden waituntilterminated; RunOnceId: "RemoveLegacyFirewallRule"
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall delete rule name=""PhantomCheck Desktop (App)"""; Flags: runhidden waituntilterminated; RunOnceId: "RemoveAppFirewallRule"
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall delete rule name=""PhantomCheck Desktop (Link)"""; Flags: runhidden waituntilterminated; RunOnceId: "RemoveLinkFirewallRule"
Filename: "{sys}\schtasks.exe"; Parameters: "/delete /tn PhantomCheckDesktopLogon /f"; Flags: runhidden waituntilterminated; RunOnceId: "RemoveAutoStartTask"

[Code]
var
  LegacyCleanedCount: Integer;

procedure RequestAppGracefulShutdown();
var
  ExistingExe: String;
  ResultCode: Integer;
begin
  ExistingExe := ExpandConstant('{localappdata}\PhantomCheckDesktop\{#MyAppExeName}');
  if not FileExists(ExistingExe) then
    ExistingExe := ExpandConstant('{app}\{#MyAppExeName}');

  if FileExists(ExistingExe) then
  begin
    Log('#496 Requesting graceful shutdown via --shutdown-existing: ' + ExistingExe);
    Exec(ExistingExe, '--shutdown-existing', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
    Sleep(900);
  end;

  { Fallback termination only if a stray legacy process (e.g. from old MSI) still holds file locks }
  Exec(ExpandConstant('{sys}\taskkill.exe'), '/IM {#MyAppExeName} /T /F', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
  Sleep(300);
end;

procedure CleanupUninstallRoot(RootKey: Integer; BaseSubKey: String);
var
  SubKeys: TArrayOfString;
  I: Integer;
  SubKeyPath, DisplayName, UninstallStr, QuietUninstallStr, CleanCmd: String;
  WinInstallerFlag: Cardinal;
  IsMsi: Boolean;
  ResultCode: Integer;
begin
  if not RegGetSubkeyNames(RootKey, BaseSubKey, SubKeys) then
    Exit;

  for I := 0 to GetArrayLength(SubKeys) - 1 do
  begin
    { Never uninstall our own current Inno Setup AppId (Inno handles in-place upgrade natively) }
    if LowerCase(SubKeys[I]) = LowerCase('{#MyAppIdGuid}_is1') then
      Continue;

    SubKeyPath := BaseSubKey + '\' + SubKeys[I];
    DisplayName := '';
    if not RegQueryStringValue(RootKey, SubKeyPath, 'DisplayName', DisplayName) then
      Continue;

    if Pos('phantomcheck', LowerCase(DisplayName)) = 0 then
      Continue;

    UninstallStr := '';
    QuietUninstallStr := '';
    RegQueryStringValue(RootKey, SubKeyPath, 'UninstallString', UninstallStr);
    RegQueryStringValue(RootKey, SubKeyPath, 'QuietUninstallString', QuietUninstallStr);

    IsMsi := False;
    if RegQueryDWordValue(RootKey, SubKeyPath, 'WindowsInstaller', WinInstallerFlag) then
      IsMsi := (WinInstallerFlag = 1);
    if (not IsMsi) and (Pos('msiexec', LowerCase(UninstallStr)) > 0) then
      IsMsi := True;

    if IsMsi then
    begin
      Log('#496 Detected legacy MSI installation: ' + DisplayName + ' (' + SubKeys[I] + ')');
      if Exec(ExpandConstant('{sys}\msiexec.exe'), '/x ' + SubKeys[I] + ' /qn /norestart', '', SW_HIDE, ewWaitUntilTerminated, ResultCode) then
      begin
        Log('#496 Legacy MSI uninstall finished with exit code ' + IntToStr(ResultCode));
        LegacyCleanedCount := LegacyCleanedCount + 1;
      end
      else
        Log('#496 Failed to invoke msiexec for ' + SubKeys[I]);
    end
    else if (Pos('unins', LowerCase(UninstallStr)) > 0) or (Pos('unins', LowerCase(QuietUninstallStr)) > 0) then
    begin
      CleanCmd := Trim(RemoveQuotes(UninstallStr));
      if (CleanCmd <> '') and FileExists(CleanCmd) then
      begin
        Log('#496 Detected legacy Inno installation: ' + DisplayName + ' -> ' + CleanCmd);
        if Exec(CleanCmd, '/VERYSILENT /NORESTART /SUPPRESSMSGBOXES', '', SW_HIDE, ewWaitUntilTerminated, ResultCode) then
        begin
          Log('#496 Legacy Inno uninstall finished with exit code ' + IntToStr(ResultCode));
          LegacyCleanedCount := LegacyCleanedCount + 1;
        end;
      end;
    end;
  end;
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
begin
  Result := '';
  NeedsRestart := False;
  LegacyCleanedCount := 0;

  Log('#496 PrepareToInstall: starting graceful shutdown & legacy MSI/Inno cleanup');
  RequestAppGracefulShutdown();

  CleanupUninstallRoot(HKCU, 'Software\Microsoft\Windows\CurrentVersion\Uninstall');
  CleanupUninstallRoot(HKLM, 'Software\Microsoft\Windows\CurrentVersion\Uninstall');
  if IsWin64 then
  begin
    CleanupUninstallRoot(HKCU, 'Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall');
    CleanupUninstallRoot(HKLM, 'Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall');
  end;

  if LegacyCleanedCount > 0 then
    Log('#496 Automatically removed ' + IntToStr(LegacyCleanedCount) + ' legacy installation(s); %APPDATA%\PhantomCheckDesktop preserved.');
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if (CurStep = ssPostInstall) and (LegacyCleanedCount > 0) then
  begin
    WizardForm.FinishedLabel.Caption :=
      WizardForm.FinishedLabel.Caption + #13#10#13#10 +
      '已自動移除舊版安裝元件（設定與配對資料已完整保留）。' + #13#10 +
      'Legacy installation components were automatically removed (your settings and pairings were preserved).';
  end;
end;
