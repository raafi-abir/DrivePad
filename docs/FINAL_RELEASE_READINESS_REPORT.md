# DrivePad v1.0.0 — Final Security Audit, Hardware Validation & Release Readiness Report

**Release Version:** `1.0.0` (`versionCode = 1`)  
**Android Application ID:** `com.drivepad.app`  
**Companion Version:** `1.0.0` (`fatJar`, Linux `tar.gz`, Windows `zip`)  
**Wire Protocol:** Protocol `v1` (`33`-byte Little-Endian UDP)  
**Assessment Status:** Stage 4 Complete — **`READY FOR PRIVATE TESTING`** ( **`NOT READY FOR PUBLIC RELEASE`** while Production Android Signing and Physical Hardware Validation remain **`BLOCKED`** )

---

## 1. Architecture & Supported Platform Matrix

DrivePad connects a physical Android phone controller (`com.drivepad.app`) to a PC virtual gamepad host over USB tethering or local 5 GHz Wi-Fi UDP:

| Component | Module / Path | Responsibilities | Runtime Prerequisites |
| :--- | :--- | :--- | :--- |
| **Android Controller** | `:app` (`com.drivepad.app`) | Steering wheel / sensor fusion, digital throttle/brake, gearbox, lights/indicators/engine switches, **Dashboard Studio** (`v1` DataStore preferences), 250 Hz monotonic UDP TX + `PING`/`PONG` RTT & watchdog | Android 7.0+ (`minSdk = 24`, `targetSdk = 36`); USB tethering or local Wi-Fi (cellular-only fallback blocked) |
| **Companion Core** | `companion/:core` | Shared 33-byte UDP `v1` decoder, session & sequence anti-replay manager, monotonic failsafe watchdog, profile curves/toggles, config & profile persistence, RTT/jitter stats | Java 21+ JVM (zero native libraries required) |
| **Linux Backend** | `companion/:linux` | `/dev/uinput` ioctl & `evdev` virtual gamepad (`drivepad-companion --backend uinput`) + non-root `udev` rules & Arch `PKGBUILD` | Linux x86_64/aarch64 kernel with `uinput` module, group `uinput`, UDP `42100` allowed |
| **Windows Backend** | `companion/:windows` | Xbox 360 (`XUSB`) virtual controller (`--backend vigem`) via explicit trusted JNA `ViGEmClient.dll` probing | Windows 10/11 x64 + manual signed **Nefarius `ViGEmBus v1.22.0`** (EOL) + x64 `ViGEmClient.dll`; **HVCI & Secure Boot remain ON** |
| **Unified CLI & Packaging** | `companion/` | Single CLI (`--backend auto|noop|uinput|vigem`), fat JAR, reproducible `.tar.gz` / `.zip`, `release-manifest.json`, `SHA256SUMS.txt` | Java 21+ JRE/JDK |

---

## 2. Security Audit & Threat Model

### 2.1 Assets, Trust Boundaries & Attack Surfaces

| Category | Specific Items |
| :--- | :--- |
| **Critical Assets** | Physical vehicle control stream (steering axis `[-1..1]`, digital throttle/brake `0/1`, gears `-2..12`, switches), virtual OS input device (`/dev/uinput` or `ViGEmBus` XUSB device), user configuration/profile `.properties` files, Windows native DLL loading path, Android DataStore settings, release signing keys |
| **Trust Boundaries** | 1. **Android Wi-Fi / USB Tether Network Boundary** (untrusted LAN hosts vs controller UDP port `42100`)<br>2. **User CLI & Config/Profile Filesystem Boundary** (`~/.config/drivepad-companion/`, `%APPDATA%\drivepad-companion\`)<br>3. **JVM -> Kernel / Native Boundary** (`/dev/uinput` ioctl write, JNA `LoadLibrary("ViGEmClient.dll")`)<br>4. **Build & Keystore Boundary** (env var signing vs Git repository) |
| **Attack Surfaces** | UDP port `42100` packet decoder & session state machine, Android UDP receive loop & RTT estimator, CLI `--profile`/`--config` flags, `.properties` deserializers, Windows DLL resolution order, Linux install scripts & `uinput` permissions, Gradle signing & artifact pipeline |
| **Potential Adversaries** | Rogue device on shared Wi-Fi LAN, spoofed off-path UDP sender, malicious `.properties` profile shared online, untrusted file planted in `Downloads`/`%TEMP%` before launching companion, accidental root execution |

### 2.2 Threat Scenarios, Impacts & Implemented Mitigations

| Threat | Potential Impact | Status / Mitigation |
| :--- | :--- | :--- |
| **Malformed / Truncated / Oversized UDP Datagrams** | Decoder exceptions, `NaN`/`Infinity` propagating to axes, non-digital throttle poisoning | **Mitigated (`PASS`)**: Strict `33`-byte length check, version `1` guard, finite bounds (`-1.0f..1.0f`), digital pedal guard (`0.0f`/`1.0f`), valid handbrake/transmission/gear/reserved flag validation. |
| **UDP Replay Attack on `CONTROLLER_STATE` / `PING`** | Attacker replays captured full-throttle packet or keeps watchdog alive indefinitely after disconnect | **Fixed in Stage 4 (`PASS`)**: `ProtocolHandler` enforces wrap-aware strictly increasing 32-bit `sequenceNumber` tracking (`lastAppliedStateSeq`, `lastAppliedPingSeq`) before updating controls, feeding `InputWatchdog`, or refreshing activity timestamps. |
| **Cross-IP Session Hijack (`HELLO` Takeover)** | Rogue LAN host sends `HELLO` mid-drive to steal active session | **Fixed in Stage 4 (`PASS`)**: `ConnectionManager.tryAcceptHandshake()` rejects competing IP `HELLO` packets while the active host is live inside `activeSessionGuardWindowNs` (matching `--watchdog-ms`), preserving same-IP crash reconnects and legitimate handovers after disconnect/timeout. |
| **Spoofed Source IP / Port `PONG` RTT Poisoning (Android)** | Unmatched `PONG` timestamps corrupt Android RTT/jitter diagnostics | **Fixed in Stage 4 (`PASS`)**: Removed unverified fallback in `ControllerConnectionManager` so only timestamps matching `pendingPings` are accepted; `pendingPings` map size bounded (`<= 256`). |
| **Path Traversal via `--profile` / `profile.active`** | Arbitrary `.properties` read outside `profiles/` (`--profile ../../etc/passwd`) | **Fixed in Stage 4 (`PASS`)**: `ProfileManager` enforces `^[a-z0-9][a-z0-9_-]{0,63}$`, canonical parent/prefix containment, regular-file & `64 KiB` size caps, and config active-profile sanitization. |
| **Config / Profile Crash & Permissive File Modes** | Malformed `\uXXXX` escape or huge file crashes startup; `0644` permissions expose local config | **Fixed in Stage 4 (`PASS`)**: `ConfigManager` guards file size (`<= 64 KiB`), recovers cleanly to defaults on parse errors, and applies POSIX `0700` dirs / `0600` files (reinforced in `install-linux.sh`). |
| **Windows DLL Search-Order Hijacking** | Trojan `ViGEmClient.dll` in `Downloads` or `%TEMP%` loaded via `user.dir` or bare `"ViGEmClient"` | **Fixed in Stage 4 (`PASS`)**: Removed `workingDir` (`user.dir`) from `WindowsHostProbe.defaultDllSearchPaths()` and removed bare `"ViGEmClient"` fallback in `NativeViGEmGamepadDriver.open()`. |
| **Log Flood / Root Privilege Overreach** | UDP flood fills disk logs; running companion as `root` widens blast radius | **Fixed in Stage 4 (`PASS`)**: `ProtocolHandler` rate-limits malformed/replay/hijack/`HELLO` warnings; `Main.kt` (`start()` & `--check`) and `install-linux.sh` detect UID 0 and warn against root execution. |

### 2.3 Remaining Architectural Limitations of Protocol v1 & Proposed Protocol v2

Because Protocol v1 (`33`-byte Little-Endian UDP, `protocolVersion = 1`) is locked for Phase 0–12 compatibility, **cryptographic authentication and encryption are NOT present on the wire**:
1. **Unencrypted Cleartext Telemetry:** Steering/pedal states and sequence numbers are visible to passive Wi-Fi/LAN sniffers (USB tethering mitigates wireless exposure).
2. **Same-IP Source Spoofing / spoofed `DISCONNECT`:** On networks without ingress source validation, an attacker able to forge the connected phone's source IP *and* ephemeral UDP source port and guess a higher `sequenceNumber` can inject inputs or spoof `DISCONNECT`.
3. **Idle Window `HELLO` Race:** When no phone is connected (or after `watchdogTimeoutMs` expiry), any reachable LAN host can claim the idle slot until firewall/interface binding restricts listeners (always bind `--interface usb0` or tether IP where possible).

**Recommended Protocol v2 Design (Future Release, Separate Wire Version `protocolVersion = 2`):**
- Keep `33`-byte Protocol v1 untouched for legacy interoperability unless opted into v2.
- Perform explicit session pairing (one-time QR/PIN 256-bit pre-shared session key or ephemeral X25519 key agreement) during `HELLO`/`HELLO_ACK` to establish a per-session secret key + 64-bit session ID.
- Append a truncated **16-byte SipHash / ChaCha20-Poly1305 AEAD tag (or HMAC-SHA256-128)** + 64-bit monotonic counter per datagram (total `~49–57` bytes, well within single-MTU 1500B UDP), authenticating sender identity, `DISCONNECT`, `PONG` echo, and replay protection against spoofed same-IP attackers.

---

## 3. Automated Verification, Security & Fuzz Test Results

All verification runs executed after the final Stage 4 security fixes:

| Suite / Command | Scope | Result | Classification |
| :--- | :--- | :--- | :--- |
| `gradle -p companion clean test packageDistributions installDist --console=plain` | 16 JUnit XML suites across `:companion`, `:core`, `:linux`, `:windows`, including `SecurityAuditAndFuzzTest` | **72 / 72 passed** (`0` failed, `0` skipped) | `PASS` (unit/loopback/fuzz) |
| `gradle :app:clean :app:testDebugUnitTest :app:compileReleaseKotlin --console=plain` | 12 JUnit/Robolectric XML suites + Release Kotlin compilation (`SecurityNetworkingTest` included) | **46 / 46 passed** (`0` failed, `0` skipped) | `PASS` (JVM/Robolectric) |
| Ephemeral Keystore Release Verification (`:app:assembleRelease`, `aapt2 dump badging`, `apksigner verify`) | Verifies missing `KEYSTORE_PATH` fails cleanly; verifies `com.drivepad.app` v1.0.0 (`versionCode=1`), least-privilege permissions (`INTERNET`, `ACCESS_NETWORK_STATE`), v2 signature; temporary `.jks` & release APK deleted automatically | **Verified cleanly; zero artifacts left in repo** | `PASS` (ephemeral test key) |
| Reproducibility Check (`packageDistributions --rerun-tasks` + `diff SHA256SUMS.txt`) | Two independent builds produce identical archive SHA-256 digests | **0-byte diff across rebuilds** | `PASS` |
| Static Hygiene Scan (`find ... *.jks *.dll *.sys *.exe`) | Ensures zero release keystores, passwords, or third-party `.dll`/`.sys`/`.exe` inside source tree or archives | **Clean** (only pre-existing dev `debug.keystore`; archives contain 0 Windows/native binaries) | `PASS` |

**Stage 4 Security & Fuzz Tests Added:**
- `companion/src/test/kotlin/com/drivepad/companion/SecurityAuditAndFuzzTest.kt` (8 tests):
  1. Seeded PRNG fuzzing of **5,000** random-size (`0..256` B) & field-mutated UDP datagrams -> `PacketDecoder` never throws; virtual gamepad remains `SafeNeutral`.
  2. Anti-replay sequence enforcement + unsigned 32-bit boundary wrap (`0xFFFFFFFE -> 0xFFFFFFFF -> 0 -> 1`).
  3. Cross-IP `HELLO` hijack rejection during active guard window, spoofed `CONTROLLER_STATE`/`PING`/`DISCONNECT` rejection, same-IP crash reconnect recovery, and post-timeout handover.
  4. Real localhost UDP socket flood (**1,500** spoofed/malformed datagrams) verifying companion availability and clean `SafeNeutral` shutdown.
  5. `ProfileManager` path traversal (`../../etc/passwd`, `..\\`, oversized `>64 KiB`, malformed escapes) + POSIX `0700`/`0600` check.
  6. `ConfigManager` malformed `\uZZZZ`, oversized `>64 KiB`, and `profile.active=../evil` sanitization.
  7. `WindowsHostProbe` untrusted CWD `ViGEmClient.dll` exclusion + `NativeViGEmGamepadDriver` refusing bare `LoadLibrary("ViGEmClient")`.
  8. `isRunningAsRoot` UID 0 detection, blank `--interface` rejection, and `/dev/uinput` `EACCES` permission error isolation.
- `app/src/test/java/com/drivepad/app/SecurityNetworkingTest.kt` (2 tests):
  1. Seeded PRNG fuzzing of **3,000** malformed/mutated packets on Android `PacketCodec.decode`.
  2. Spoofed IP/port packet rejection & forged/unmatched `PONG` timestamp rejection in `UdpControllerConnectionManager`.

---

## 4. Arch Linux Installation & Physical Hardware Validation Guide

> **Verification Status:** Automated packaging and `noop` CLI checks **`PASS`**. Live Arch Linux kernel `/dev/uinput`, USB tether, `evtest`, Steam Input, and ETS2 checks remain **`BLOCKED`** inside the headless build container and must be executed on target Arch hardware.

### 4.1 Prerequisites & Portable Install (Non-Root User Scope)
1. **Install Java 21+ JRE/JDK** (plus `evtest` / `sdl2` optional diagnostics):
   ```bash
   sudo pacman -Syu jre21-openjdk
   # Optional inspection tools:
   sudo pacman -S evtest sdl2
   ```
2. **Verify archive checksum, extract, and install into `~/.local/` (do NOT run as `root`):**
   ```bash
   sha256sum -c SHA256SUMS.txt
   tar -xzf drivepad-companion-1.0.0-linux-x64.tar.gz
   cd drivepad-companion-1.0.0
   ./scripts/install-linux.sh
   # Ensures ~/.local/bin is in PATH; ~/.config/drivepad-companion gets chmod 0700
   ```
   *Alternative Arch Native Package (`PKGBUILD`):* Copy `companion/packaging/linux/arch/PKGBUILD`, `drivepad-companion.install`, and the `.tar.gz` into a clean directory, update checksums (`updpkgsums`), and install with `makepkg -si`.

### 4.2 Load `uinput` & Configure Unprivileged `udev` Access
```bash
sudo modprobe uinput
echo "uinput" | sudo tee /etc/modules-load.d/uinput.conf
sudo cp udev/99-drivepad-uinput.rules /etc/udev/rules.d/
sudo groupadd -f uinput
sudo usermod -aG uinput "$USER"
sudo udevadm control --reload-rules && sudo udevadm trigger --name-match=uinput
# Log out and back in so 'uinput' group membership takes effect!
ls -l /dev/uinput
# Expected: crw-rw---- 1 root uinput 10, 223 ... /dev/uinput
```

### 4.3 Firewall & Interface Discovery (Verified CLI Flags)
1. Permit UDP `42100`:
   ```bash
   # ufw:
   sudo ufw allow 42100/udp
   # OR firewalld:
   sudo firewall-cmd --add-port=42100/udp --permanent && sudo firewall-cmd --reload
   ```
2. Connect Android USB tethering (preferred: lowest jitter; interfaces typically `usb0`, `rndis0`, or `enp*u*` / `enx*`) or 5 GHz Wi-Fi (`wlan0`), then verify interfaces & readiness:
   ```bash
   drivepad-companion --list-interfaces
   drivepad-companion --check
   ```
3. Launch DrivePad Companion bound explicitly to the tether/LAN interface:
   ```bash
   drivepad-companion --backend uinput --profile ets2 --interface usb0
   # Replace 'usb0' with your actual interface name or IPv4 from --list-interfaces
   ```
   *(All supported flags verified in `Main.kt`: `--port`/`-p`, `--interface`/`-i`/`--bind`/`-b`, `--list-interfaces`, `--watchdog-ms`/`-w`, `--backend`/`-B` [`auto|noop|uinput|vigem`], `--profile`/`-P`, `--list-profiles`, `--device-name`, `--log-level`/`-l`, `--diagnostics`/`-d`, `--config`/`-c`, `--save-config`, `--check`/`--doctor`, `--version`/`-V`, `--help`/`-h`.)*

### 4.4 Hardware Validation Checklist (To Sign Off on Physical Machine)
- **`BLOCKED` Check L1 — Virtual Device & `evtest`:** Run `sudo evtest` (or `evtest` if member of `input`), select `DrivePad Virtual Gamepad`, confirm `ABS_X` steering sweep `-32767..32767`, `ABS_RZ` throttle, `ABS_Z` brake, handbrake/gears/lights/horn/wipers/engine buttons.
- **`BLOCKED` Check L2 — Steam Input & ETS2:** Open Steam -> Settings -> Controller -> Test Device Inputs; launch ETS2 -> Options -> Controls -> select **Controller: DrivePad Virtual Gamepad**, verify steering linearity & pedal axes.
- **`BLOCKED` Check L3 — Failsafe Neutral:** While holding throttle & steering, disable Wi-Fi / unplug USB OR press `Ctrl+C`; confirm `evtest` shows `ABS_X=0`, `ABS_RZ=0`, `ABS_Z=0`, all buttons released (`0`).

---

## 5. Android & Windows 10/11 Hardware Validation Runbooks

### 5.1 Android (`com.drivepad.app`) Runbook — `BLOCKED` in Container
1. Install APK signed with private production/test upload key on physical Android 10–15 device.
2. Confirm adaptive icon renders cleanly across circle, squircle, rounded-square, and teardrop launchers.
3. Confirm cellular-only guard: disable Wi-Fi & USB tethering -> verify DrivePad blocks TX cleanly with warning.
4. Enable USB tethering (or 5 GHz Wi-Fi), enter PC IP + UDP port `42100` -> **Test Connection** -> **Start Telemetry** -> rotate landscape -> verify Steering, Digital Throttle/Brake (engine OFF lock verified), Gears, Hazard/Indicators/Lights/Wiper/Engine red-outline toggles, and **Dashboard Studio** layout/gauge persistence across app restart.

### 5.2 Windows 10 / Windows 11 x64 (`ViGEmBus`) Runbook — `BLOCKED` in Container
1. **Do NOT disable HVCI (Memory Integrity) or Secure Boot.**
2. Manually install official Microsoft-signed **Nefarius `ViGEmBus v1.22.0`** (disclosed **Archived / EOL Nov 2023**) and place 64-bit `ViGEmClient.dll` in `native\windows\` or `%APPDATA%\drivepad-companion\ViGEmClient.dll` (note: DrivePad deliberately ignores CWD `ViGEmClient.dll` to block DLL planting).
3. Run `drivepad-companion.bat --check` -> allow Windows Firewall UDP `42100` -> `drivepad-companion.bat --backend vigem --profile ets2`.
4. Validate Xbox 360 Virtual Controller in `joy.cpl`, Steam Input, and ETS2 (note Windows Xbox Game Bar Guide shortcut conflict with default `Engine` Guide mapping if enabled).

---

## 6. Reliability & Performance Metrics (Clear Simulation vs Hardware Separation)

| Metric | Measured Loopback Result (`ReliabilityAndSoakTest`, Linux JVM) | Method & Important Caveats | Hardware Target / Status |
| :--- | :--- | :--- | :--- |
| **Sustained Update Rate** | **254.6 Hz** (target `250 Hz`, `1,021` packets over `4.01 s`) | `SIMULATED`: Real UDP socket on `127.0.0.1` inside headless Linux container | `BLOCKED` on 30-min physical USB tether & Wi-Fi soak |
| **Loopback RTT (Client-Measured)** | **0.245 ms avg / 0.418 ms max** | `SIMULATED`: **Round-Trip Time (`PING`/`PONG`)** on loopback — **NOT** one-way radio/USB latency | Physical USB tether (~0.5–3 ms) & 5 GHz Wi-Fi (~2–15 ms): `BLOCKED` |
| **Server Inter-Arrival Jitter** | **0.892 ms** (RFC 3550 EWMA) | `SIMULATED`: JVM loopback thread scheduling | `BLOCKED` on real Android sensor/network loop |
| **Packet Loss / Malformed Count** | **0.000%** (`0` gaps, `0` invalid over `1,021` pkts) | `SIMULATED`: Loopback UDP queue | Real Wi-Fi interference recovery: `BLOCKED` |
| **Sender CPU / Heap Overhead** | **620 ms CPU / 919 KB heap delta** (`~4 s` burst) | `SIMULATED`: Zero per-packet allocation in 250 Hz loop | Long-soak memory stability: `BLOCKED` |

---

## 7. Release Artifacts, SHA-256 Checksums, Licensing & Signing

Built reproducibly (`gradle -p companion clean test packageDistributions installDist`) inside `companion/build/distributions/`:

| Artifact | Size | SHA-256 Digest | Notes |
| :--- | :--- | :--- | :--- |
| `drivepad-companion-1.0.0-linux-x64.tar.gz` | 5,276,868 B | `84a75ba6a231c6ab3aab519e9dee4ee73f89dd792e2efc2e9a9a552ae1603d64` | Contains fat JAR, POSIX launcher, install/uninstall scripts, `99-drivepad-uinput.rules`, Arch `PKGBUILD`, `THIRD_PARTY_NOTICES.md`. **Zero native binaries.** |
| `drivepad-companion-1.0.0-windows-x64.zip` | 5,279,262 B | `7ca475fbd9a9b3fcc7de38739755da830ae6387fb0d2c86f4f278739b009a16e` | Contains fat JAR, `.bat`/`.ps1` installers, manual `ViGEmClient.dll` README, `THIRD_PARTY_NOTICES.md`. **Zero `.dll`/`.sys`/`.exe` bundled.** |
| `drivepad-companion-1.0.0-all.jar` | 5,581,074 B | `72db31c867874e0ef153d47f51b99050ffc5a9c8203cd1c20ba4a32042762c48` | Standalone Java 21+ executable JAR (`COMPANION_VERSION=1.0.0`, `X-DrivePad-Protocol-Version=1`). |

- **Licensing:** Full dependency license obligations (`Apache-2.0` Kotlin/Coroutines/AndroidX/Compose/JNA elected under Apache-2.0, Linux UAPI `uinput` boundary, unbundled `ViGEmBus` `BSD-3-Clause`) are documented in `docs/THIRD_PARTY_NOTICES.md` and embedded in both archives.
- **Android Production Signing (`BLOCKED`):** `app/build.gradle.kts` reads `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`, and `KEY_ALIAS` exclusively from environment variables. No production keystore is stored in the container. Operators building public releases follow `docs/RELEASE_AND_INSTALLATION_GUIDE.md` Section 2 (offline RSA-4096 upload key + Play App Signing recovery).
- **Package ID Migration Impact (`com.drivepad.app`):** Replaced prototype `com.aistudio.drivepad.kxmpzq` permanently; prior prototype installs will not upgrade in place and must be uninstalled cleanly before installing `com.drivepad.app`.

---

## 8. Final Release Readiness Verdict

- **Automated Software, Security & Packaging Gates:** **`PASS`** (118/118 total automated tests passing, 8/8 Stage 4 security hardening fixes & regression/fuzz tests verified, reproducible companion archives built cleanly, Android release signing pipeline verified cleanly).
- **Final Release Decision:**
  - **`READY FOR PRIVATE TESTING`**: Controlled beta / developer evaluation on personal Android devices, Arch/Linux desktops, and Windows 10/11 machines using operator-signed test builds and manual hardware checkups.
  - **`NOT READY FOR PUBLIC RELEASE`**: Public distribution remains **`BLOCKED`** until:
    1. Production Android APK/AAB is signed outside this container using the guarded release upload keystore.
    2. Physical hardware validation (`L1–L3`, Android USB/Wi-Fi soak, Windows `ViGEmBus` + HVCI + ETS2) is executed and signed off on real devices.
