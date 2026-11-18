DrivePad Companion v1.0.0 — Windows x64 Distribution
======================================================

Requirements:
- Windows 10 or Windows 11 (x64)
- 64-bit Java Runtime Environment (JRE) 21 or newer (e.g., Eclipse Temurin 21 x64),
  OR an operator-supplied jlink runtime placed inside .\runtime\
- Manually installed Microsoft-signed ViGEmBus v1.22.0 + 64-bit ViGEmClient.dll
  (see native\windows\README-ViGEm.txt — ViGEmBus is archived/EOL and NOT bundled)

Quick Start:
1. Extract this ZIP and optionally run user-scope installer in PowerShell:
     powershell -ExecutionPolicy Bypass -File .\scripts\install-windows.ps1 -AddToUserPath
2. Verify Java, ViGEmClient.dll detection, and IPv4 adapters:
     .\bin\drivepad-companion.bat --check
     .\bin\drivepad-companion.bat --list-interfaces
3. Allow UDP port 42100 in Windows Defender Firewall on Private networks:
     New-NetFirewallRule -DisplayName "DrivePad UDP 42100" -Direction Inbound -Protocol UDP -LocalPort 42100 -Action Allow -Profile Private
4. Launch with Xbox 360 virtual gamepad and ETS2 profile:
     .\bin\drivepad-companion.bat --backend vigem --profile ets2
5. Press Ctrl+C to shut down safely (Xbox 360 virtual device snaps to neutral cleanly).

Uninstall:
  powershell -ExecutionPolicy Bypass -File .\scripts\uninstall-windows.ps1
  (Pass -Purge to also delete %APPDATA%\drivepad-companion)

Hardware Status Note:
  Windows ViGEmBus virtual controller support is implemented and JVM/mock-tested;
  physical Windows 10/11 + HVCI + ETS2 validation remains BLOCKED until tested on
  real Windows hardware.

Licensing:
  See THIRD_PARTY_NOTICES.md in this archive for Apache-2.0, JNA, and ViGEmBus (BSD-3-Clause) notices.
