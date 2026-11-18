param(
    [string]$InstallDir = (Join-Path $env:LOCALAPPDATA 'Programs\DrivePadCompanion'),
    [switch]$Purge
)

$ErrorActionPreference = 'Stop'
$BinDir = Join-Path $InstallDir 'bin'
$ConfigDir = Join-Path $env:APPDATA 'drivepad-companion'

$CurrentPath = [Environment]::GetEnvironmentVariable('Path', 'User')
if ($CurrentPath) {
    $Filtered = ($CurrentPath -split ';') | Where-Object { $_ -ne '' -and $_ -ne $BinDir }
    [Environment]::SetEnvironmentVariable('Path', ($Filtered -join ';'), 'User')
}

if (Test-Path $InstallDir) {
    Remove-Item -Path $InstallDir -Recurse -Force
    Write-Host "Removed DrivePad Companion installation: $InstallDir"
} else {
    Write-Host "Installation folder not found (nothing to remove): $InstallDir"
}

if ($Purge -and (Test-Path $ConfigDir)) {
    Remove-Item -Path $ConfigDir -Recurse -Force
    Write-Host "Purged user configuration directory: $ConfigDir"
} else {
    Write-Host "User config preserved at: $ConfigDir (pass -Purge to remove)."
}
