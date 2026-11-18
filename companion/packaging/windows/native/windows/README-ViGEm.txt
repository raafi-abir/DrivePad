Windows Virtual Xbox 360 Driver Notice (ViGEmBus)
==================================================

DrivePad Companion does NOT bundle or redistribute ViGEmBus.sys or ViGEmClient.dll,
and never silently installs kernel drivers or alters Windows security settings.

Important End-of-Life Disclosure:
- Nefarius ViGEmBus was officially archived / retired (EOL) in November 2023.
- Use signed Nefarius ViGEmBus v1.22.0 at your own evaluation; future Windows
  kernel or HVCI updates cannot be guaranteed compatible upstream.
- Do NOT disable HVCI (Memory Integrity) or Secure Boot as a setup workaround.

Manual Prerequisite Setup:
1. Install signed Nefarius ViGEmBus v1.22.0 (x64) from the official upstream GitHub release.
2. Place 64-bit ViGEmClient.dll in ANY of these locations:
   - This folder (native\windows\ViGEmClient.dll)
   - Working directory next to the JAR
   - %APPDATA%\drivepad-companion\ViGEmClient.dll
   - C:\Program Files\Nefarius Software Solutions\ViGEmClient\ViGEmClient.dll
   - Or on Windows PATH / C:\Windows\System32\
3. Run:
   bin\drivepad-companion.bat --check
