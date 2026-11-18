# DrivePad — Release, Installation & Operator Guide (v1.0.0)

**App Release Version:** `1.0.0` (`versionCode = 1`)  
**Companion Version:** `1.0.0`  
**Wire Protocol Version:** `v1` (33-byte Little-Endian UDP — independent of app release version)  

---

## 1. Versioning & Release Architecture

DrivePad versions the Android application (`DRIVEPAD_VERSION_NAME`), PC companion (`COMPANION_VERSION`), Linux package, and Windows package together at `1.0.0`, while keeping **Protocol v1** (`PROTOCOL_VERSION = 1`) decoupled so bugfix/packaging updates never break wire compatibility.

Three independently distributable artifacts are produced:
1. **Android APK (`app`)**: Built from `:app` (`com.drivepad.app`, `minSdk = 24`, `targetSdk = 36`).
2. **Linux Companion Distribution**: `companion/build/distributions/drivepad-companion-1.0.0-linux-x64.tar.gz` + Arch Linux `companion/packaging/linux/arch/PKGBUILD`.
3. **Windows x64 Companion Distribution**: `companion/build/distributions/drivepad-companion-1.0.0-windows-x64.zip`.

Verify version and environment readiness on any PC companion binary via:
```bash
drivepad-companion --version
drivepad-companion --check
```

---

## 2. Android Build, Keystore & Release Signing Guide

### 2.1 Debug / Local Development Builds (No Release Secrets Required)
```bash
# Run Android JVM & Robolectric test suite
gradle :app:testDebugUnitTest --console=plain

# Build debug APK using local auto-generated debug.keystore
gradle :app:assembleDebug --console=plain
```

### 2.2 Generating & Protecting a Production Upload Keystore
Never commit keystores (`.jks` / `.keystore`) or passwords to Git, Gradle files, or source code.

1. **Generate an encrypted RSA-2048/4096 upload key outside the repository directory:**
   ```bash
   mkdir -p ~/.keys
   keytool -genkeypair -v \
     -keystore ~/.keys/drivepad-upload-key.jks \
     -alias upload \
     -keyalg RSA -keysize 4096 -validity 10000
   chmod 600 ~/.keys/drivepad-upload-key.jks
   ```
2. **Back up the keystore and passwords offline** (password manager + encrypted hardware backup). Losing your upload key prevents updating published APKs unless Play App Signing key reset is used.

### 2.3 Building & Verifying the Signed Release APK
Supply signing credentials exclusively via environment variables (`signingConfigs.release` in `app/build.gradle.kts`):
```bash
export KEYSTORE_PATH="$HOME/.keys/drivepad-upload-key.jks"
export STORE_PASSWORD="<your-keystore-password>"
export KEY_PASSWORD="<your-key-password>"

gradle :app:assembleRelease --console=plain
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```
Output APK: `app/build/outputs/apk/release/app-release.apk` (not committed or uploaded automatically).

### 2.4 Keystore Backup & Recovery Policy
- **Backup:** Store at least two encrypted offline backups of `drivepad-upload-key.jks` + alias (`upload`) + SHA-256 fingerprint (`keytool -list -v`) in a hardware vault/password manager outside the Git workspace.
- **Recovery / Play App Signing:** If distributing via Google Play App Signing, enroll with the Play-managed app signing key and use `drivepad-upload-key.jks` solely as the upload key so Google Play Support can reset a compromised or lost upload key without breaking existing user upgrades. Direct sideload distributions cannot change signing keys without uninstalling previous installs.

### 2.5 Application Identity Migration (`com.drivepad.app`) & Compatibility
- **Permanent ID:** `com.drivepad.app` replaced the transitional prototype ID `com.aistudio.drivepad.kxmpzq` in Phase 12 Stage 3.
- **Non-In-Place Upgrade Impact:** Android treats distinct `applicationId` values as completely separate applications. Early test builds signed under `com.aistudio.drivepad.kxmpzq` **will NOT upgrade in place** to `com.drivepad.app` and can coexist side-by-side until manually uninstalled.
- **Settings Isolation:** Local Jetpack DataStore preferences (`drivepad_user_preferences`, saved companion host/port, and Dashboard Studio layout settings) live inside the prior package sandbox and do not automatically migrate across package IDs; re-enter the companion IP once after installing `com.drivepad.app`.
- **External Integrations Check:** `google-services.json`, OAuth client configs, Play Console listings, and custom deep links are currently unused by DrivePad (`googleServices.missing.passthrough=true`). If Firebase, OAuth, or Play Console integration is added later, register `com.drivepad.app` and the production signing certificate SHA-1/SHA-256 explicitly.

---

## 3. Linux Companion Installation, Upgrade & Uninstall

### Prerequisites
- 64-bit Linux (`x86_64` or `aarch64`) with kernel `uinput` module.
- Java Runtime Environment (JRE) **21 or newer** (`sudo pacman -S jre21-openjdk` on Arch, or `sudo apt install openjdk-21-jre` on Debian/Ubuntu).

### Build Portable Distribution
```bash
gradle -p companion linuxDistTar --console=plain
# Artifact: companion/build/distributions/drivepad-companion-1.0.0-linux-x64.tar.gz
```

### Install (Portable Archive — Non-Root User Scope)
1. Extract and run the user installer (installs into `~/.local/bin` & `~/.local/share/drivepad-companion`):
   ```bash
   tar -xzf drivepad-companion-1.0.0-linux-x64.tar.gz
   cd drivepad-companion-1.0.0
   ./scripts/install-linux.sh
   ```
2. Configure non-root `/dev/uinput` permissions once (never run DrivePad Companion as `root`):
   ```bash
   sudo modprobe uinput
   echo "uinput" | sudo tee /etc/modules-load.d/uinput.conf
   sudo cp udev/99-drivepad-uinput.rules /etc/udev/rules.d/
   sudo groupadd -f uinput
   sudo usermod -aG uinput "$USER"
   sudo udevadm control --reload-rules && sudo udevadm trigger --name-match=uinput
   # Log out and back in so 'uinput' group membership takes effect.
   ```
3. Allow inbound UDP port `42100` in your firewall:
   ```bash
   sudo ufw allow 42100/udp
   # or firewalld:
   sudo firewall-cmd --add-port=42100/udp --permanent && sudo firewall-cmd --reload
   ```

### Arch Linux (`PKGBUILD`)
Copy `companion/packaging/linux/arch/PKGBUILD`, `drivepad-companion.install`, and the built `.tar.gz` into a clean build folder and run:
```bash
makepkg -si
sudo usermod -aG uinput "$USER"
```

### Upgrade & Uninstall (Reversible)
- **Upgrade:** Re-run `./scripts/install-linux.sh` from a newer archive (overwrites JAR/launcher while preserving `~/.config/drivepad-companion/`).
- **Uninstall (keep config/profiles):** `./scripts/uninstall-linux.sh`
- **Uninstall + remove config/profiles:** `./scripts/uninstall-linux.sh --purge`

---

## 4. Windows x64 Companion Installation, Upgrade & Uninstall

### Prerequisites & Important `ViGEmBus` Disclosures
- **Windows 10 or Windows 11 (64-bit x64)**
- **Java 21+ (64-bit)** (e.g., Eclipse Temurin 21 x64) or an operator-supplied `jlink` runtime placed in `.\runtime\` next to `bin\`.
- **`ViGEmBus` End-of-Life Disclosure:**
  - Nefarius **`ViGEmBus` (`v1.22.0`) is officially archived / End-of-Life (Nov 2023)**. Future Windows kernel updates cannot be guaranteed compatible upstream.
  - DrivePad **never silently installs drivers**, **never bundles unverified third-party binaries**, and **must NOT ask users to disable HVCI (Memory Integrity) or Secure Boot**.
  - Manually install Microsoft-signed `ViGEmBus_1.22.0_x64_x86_arm64.exe` from official Nefarius releases and place 64-bit `ViGEmClient.dll` alongside `drivepad-companion-1.0.0-all.jar`, in `native\windows\ViGEmClient.dll`, `%APPDATA%\drivepad-companion\ViGEmClient.dll`, or on `%PATH%`.

### Build Portable Distribution
```bash
gradle -p companion windowsDistZip --console=plain
# Artifact: companion/build/distributions/drivepad-companion-1.0.0-windows-x64.zip
```

### Install & Firewall Setup (PowerShell)
1. Extract `drivepad-companion-1.0.0-windows-x64.zip` and run the user-scope installer (`%LOCALAPPDATA%\Programs\DrivePadCompanion`):
   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\install-windows.ps1 -AddToUserPath
   ```
2. Allow UDP port `42100` on Private networks (Elevated PowerShell):
   ```powershell
   New-NetFirewallRule -DisplayName "DrivePad UDP 42100" -Direction Inbound -Protocol UDP -LocalPort 42100 -Action Allow -Profile Private
   ```

### Upgrade & Uninstall (Reversible)
- **Upgrade:** Re-run `install-windows.ps1` from the newer ZIP (preserves `%APPDATA%\drivepad-companion\`).
- **Uninstall (keep config):** `powershell -ExecutionPolicy Bypass -File .\scripts\uninstall-windows.ps1`
- **Uninstall + remove config:** `powershell -ExecutionPolicy Bypass -File .\scripts\uninstall-windows.ps1 -Purge`

---

## 5. First-Run Connection Guide (Step-by-Step)

1. **Install/Extract** DrivePad Companion using Section 3 (Linux) or Section 4 (Windows).
2. **Verify Readiness (`--check`):** Run `drivepad-companion --check` to confirm Java, OS, DrivePad/Companion/Protocol versions, config paths, and backend availability (`/dev/uinput` or `ViGEmClient.dll`).
3. **Connect Network:** Enable Android **USB tethering** (lowest latency/jitter) or connect phone & PC to the same 5 GHz Wi-Fi LAN.
4. **Discover Interfaces:** Run `drivepad-companion --list-interfaces` and note the tether (`usb0`/`rndis0`/`Remote NDIS`) or LAN IPv4 address.
5. **Launch Companion:**
   - Linux: `drivepad-companion --backend uinput --profile ets2 --interface usb0`
   - Windows: `drivepad-companion.bat --backend vigem --profile ets2`
6. **Confirm Listening Status:** Verify `Listening: <ip>:42100` and `Virtual Device: ACTIVE`.
7. **Connect Android App:** Open DrivePad -> enter PC IPv4 & port `42100` -> tap **Test Connection** -> **Start Telemetry** -> rotate to landscape Cockpit.
8. **Verify Diagnostics:** Watch RTT, Hz (~250 Hz), packet count, and input bars update live on both Android Telemetry Diagnostics and the companion terminal dashboard.
9. **Shut Down Safely:** Press `Ctrl+C` in the companion terminal or tap **Disconnect** in DrivePad — virtual gamepad axes/pedals snap cleanly to `SafeNeutral`.

---

## 6. Troubleshooting Guide

| Symptom | Likely Cause | Resolution |
| :--- | :--- | :--- |
| `/dev/uinput missing (run: sudo modprobe uinput)` | Kernel module not loaded | Run `sudo modprobe uinput` and persist via `/etc/modules-load.d/uinput.conf`. |
| `Permission denied on /dev/uinput` | User not in `uinput` group or udev rule missing | Install `udev/99-drivepad-uinput.rules`, add user to `uinput`, reload udev, and re-login. |
| `ViGEmClient.dll not in explicit search dirs` | DLL missing or x86 mismatch | Place **64-bit** `ViGEmClient.dll` in `native\windows\`, next to JAR, or in `%APPDATA%\drivepad-companion\`. |
| `VIGEM_ERROR_BUS_NOT_FOUND` | `ViGEmBus v1.22.0` driver not installed | Install signed Nefarius `ViGEmBus v1.22.0` manually; verify Windows didn't block driver. |
| Android stays in `CONNECTING` | Firewall blocking UDP `42100` or wrong interface/IP | Allow inbound UDP `42100`, run `--list-interfaces`, verify PC IP matches tether/LAN subnet. |
| `Cellular data only` warning on Android | USB tether off & Wi-Fi off | Enable USB tethering or connect to local Wi-Fi (cellular telemetry is blocked). |
| Xbox Game Bar opens on `Engine` button (Windows) | Windows maps Xbox Guide button to Game Bar | Disable Game Bar Guide shortcut in Windows Settings or set `button.engine.code=LEFT_THUMB` in `ets2.properties`. |

---

## 7. Known Limitations & Hardware Validation Status

Per [`docs/PHASE_11_HARDWARE_VALIDATION_REPORT.md`](PHASE_11_HARDWARE_VALIDATION_REPORT.md):
- Automated tests pass in **SIMULATED** mode (Android unit/Robolectric tests, companion unit/mock/loopback UDP tests, packaging/script checks).
- **Physical hardware validation remains `BLOCKED` in the headless build sandbox** and has NOT yet been run on real Linux desktop `/dev/uinput`, Windows 10/11 + HVCI + `ViGEmBus`, physical Android phones, Steam Input, or live ETS2.
- `ViGEmBus` is archived/EOL (Nov 2023); `:windows` backend isolation allows future replacement with a maintained virtual controller driver without touching `:core` or Protocol v1.
- External ETS2 `scs-sdk-plugin` channels (`Fuel`, `Coolant`, `Oil`, `Air`, `Speed Limit`) remain `Unavailable` (`N/A`).

---

## 8. Licensing & Third-Party Notices

Full dependency license audits (`Apache-2.0` AndroidX/Compose/Kotlin/Coroutines/Okio, `net.java.dev.jna:jna` dual `Apache-2.0`/`LGPL-2.1-or-later` elected under `Apache-2.0`, Linux `uinput` UAPI boundary, and external unbundled `ViGEmBus`/`ViGEmClient` `BSD-3-Clause` disclosures) are documented in [`docs/THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) and bundled as `THIRD_PARTY_NOTICES.md` inside both companion distributions.

---

## 9. Release Checklist

- [x] Permanent Android Application ID migrated to `com.drivepad.app`
- [x] Zero debug-only libraries (`firebase-appcheck-debug`) or hardcoded signing secrets
- [x] Companion `fatJar`, `linuxDistTar`, and `windowsDistZip` assembled deterministically with `THIRD_PARTY_NOTICES.md` & `SHA256SUMS.txt`
- [x] Stage 4 security audit, replay/hijack/traversal/DLL-hijack hardening & fuzz suites verified ([`docs/FINAL_RELEASE_READINESS_REPORT.md`](FINAL_RELEASE_READINESS_REPORT.md))
- [x] All automated Android (46/46) & PC companion (72/72) tests passing after final edits
- [ ] Production Android release signing (`BLOCKED` — requires private `KEYSTORE_PATH` outside build container)
- [ ] Physical Linux (`/dev/uinput`, `evtest`, Steam Input, ETS2, Arch `PKGBUILD`) tested on hardware (`BLOCKED`)
- [ ] Physical Windows 10 & Windows 11 (`ViGEmBus v1.22.0`, HVCI ON, `joy.cpl`, Steam Input, ETS2) tested on hardware (`BLOCKED`)
- [ ] 30-minute physical USB tether & Wi-Fi soak tests signed off (`BLOCKED`)
