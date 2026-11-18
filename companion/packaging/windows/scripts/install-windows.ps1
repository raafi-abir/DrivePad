param(
    [string]$InstallDir = (Join-Path $env:LOCALAPPDATA 'Programs\DrivePadCompanion'),
    [switch]$AddToUserPath
)

$ErrorActionPreference = 'Stop'
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$DistRoot = Split-Path -Parent $ScriptDir
$BinDest = Join-Path $InstallDir 'bin'
$LibDest = Join-Path $InstallDir 'lib'
$NativeDest = Join-Path $InstallDir 'native\windows'
$ConfigDir = Join-Path $env:APPDATA 'drivepad-companion'
$ProfilesDir = Join-Path $ConfigDir 'profiles'

Write-Host "Installing DrivePad Companion into: $InstallDir"
New-Item -ItemType Directory -Path $BinDest, $LibDest, $NativeDest, $ProfilesDir -Force | Out-Null

Copy-Item -Path (Join-Path $DistRoot 'bin\*') -Destination $BinDest -Force
Copy-Item -Path (Join-Path $DistRoot 'lib\drivepad-companion-*-all.jar') -Destination $LibDest -Force
if (Test-Path (Join-Path $DistRoot 'native\windows\README-ViGEm.txt')) {
    Copy-Item -Path (Join-Path $DistRoot 'native\windows\README-ViGEm.txt') -Destination $NativeDest -Force
}
if (Test-Path (Join-Path $DistRoot 'README.txt')) {
    Copy-Item -Path (Join-Path $DistRoot 'README.txt') -Destination $InstallDir -Force
}
if (Test-Path (Join-Path $DistRoot 'THIRD_PARTY_NOTICES.md')) {
    Copy-Item -Path (Join-Path $DistRoot 'THIRD_PARTY_NOTICES.md') -Destination $InstallDir -Force
}

if ($AddToUserPath) {
    $CurrentPath = [Environment]::GetEnvironmentVariable('Path', 'User')
    $Entries = if ($CurrentPath) { $CurrentPath -split ';' | Where-Object { $_ -ne '' } } else { @() }
    if ($Entries -notcontains $BinDest) {
        $NewPath = ($Entries + $BinDest) -join ';'
        [Environment]::SetEnvironmentVariable('Path', $NewPath, 'User')
        Write-Host "Added $BinDest to your User PATH (reopen terminal to take effect)."
    }
}

Write-Host ""
Write-Host "DrivePad Companion installed successfully."
Write-Host "  Launcher : $(Join-Path $BinDest 'drivepad-companion.bat')"
Write-Host "  Config   : $ConfigDir"
Write-Host ""
Write-Host "Important Prerequisites & Disclosures:"
Write-Host "  1. ViGEmBus is NOT silently installed and is officially archived/EOL (Nov 2023)."
Write-Host "     Do NOT disable HVCI/Core Isolation or Secure Boot."
Write-Host "     Download signed ViGEmBus v1.22.0 + 64-bit ViGEmClient.dll manually from Nefarius releases."
Write-Host "  2. Allow UDP port 42100 in Windows Defender Firewall (Elevated PowerShell):"
Write-Host "     New-NetFirewallRule -DisplayName 'DrivePad UDP 42100' -Direction Inbound -Protocol UDP -LocalPort 42100 -Action Allow -Profile Private"
Write-Host "  3. Verify environment readiness:"
Write-Host "     & '$(Join-Path $BinDest 'drivepad-companion.bat')' --check"
