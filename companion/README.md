# DrivePad Companion (Cross-Platform Linux + Windows Virtual Gamepad & ETS2 Engine)

Cross-platform Kotlin/JVM companion daemon for **DrivePad** supporting **Linux (`/dev/uinput`)** and **Windows (`ViGEmBus` virtual Xbox 360 `XUSB`)**. It receives 33-byte little-endian UDP controller packets from the Android DrivePad app over USB tethering or local Wi-Fi, validates inputs, applies shared configurable controller profiles (`generic` and `ets2`), enforces a monotonic-clock failsafe watchdog, tracks network RTT/jitter telemetry, and exposes a real OS virtual gamepad via pluggable backend modules.

---

## Multi-Module Companion Architecture

```text
companion/
├── core/        # Shared OS-independent logic: 33B UDP protocol v1, UDP server, watchdog,
│                # monitoring, ConfigManager, ProfilePresets/Manager/Mapper, AsyncProfileWriterLoop
├── linux/       # Linux virtual gamepad backend: /dev/uinput ABI structs + JNA LibC + UInputGamepadBackend
├── windows/     # Windows virtual gamepad backend: ViGEmClient.dll JNA FFI + XusbReport + ViGEmX360GamepadBackend
└── src/         # Unified CLI launcher (Main.kt, PlatformBackendFactory.kt) & cross-platform E2E tests
```

- `:core` has **zero** dependencies on Linux `uinput` or Windows Win32/ViGEm APIs (enforced at compile time via Gradle).
- Wire protocol compatibility with the Android app (33-byte UDP v1, `HELLO`/`HELLO_ACK`/`CONTROLLER_STATE`/`PING`/`PONG`/`DISCONNECT`) is 100% preserved across both platforms.

---

## Building & Packaging Distributions

From the repository root:

```bash
# Run all tests across :core, :linux, :windows, and root E2E/packaging suites
gradle -p companion test --console=plain

# Build runnable fat JAR, Linux portable archive (.tar.gz + Arch PKGBUILD), and Windows x64 archive (.zip)
gradle -p companion packageDistributions --console=plain
```

Generated artifacts under `companion/build/`:
- `libs/drivepad-companion-1.0.0-all.jar` (standalone cross-platform fat JAR)
- `distributions/drivepad-companion-1.0.0-linux-x64.tar.gz` (Linux launcher, udev rule, `install-linux.sh`/`uninstall-linux.sh`, Arch `PKGBUILD`, `THIRD_PARTY_NOTICES.md`)
- `distributions/drivepad-companion-1.0.0-windows-x64.zip` (Windows `.bat`/`.ps1` launchers, `install-windows.ps1`/`uninstall-windows.ps1`, `ViGEmBus` disclosure, `THIRD_PARTY_NOTICES.md` — no third-party driver binaries bundled)
- `distributions/release-manifest.json` & `distributions/SHA256SUMS.txt`

For complete installation, upgrade, uninstall, licensing notices, and Android release signing instructions (`com.drivepad.app`), see [`../docs/RELEASE_AND_INSTALLATION_GUIDE.md`](../docs/RELEASE_AND_INSTALLATION_GUIDE.md) and [`../docs/THIRD_PARTY_NOTICES.md`](../docs/THIRD_PARTY_NOTICES.md).

---

## Running (Linux & Windows)

### Auto-Select OS Backend (`--backend auto`)
Automatically uses `uinput` on Linux hosts and `vigem` (Xbox 360) on Windows hosts:

```bash
java -jar companion/build/libs/drivepad-companion-1.0.0-all.jar --backend auto --profile ets2
```

### Safe Dry-Run Mode (`--backend noop`, default when flag omitted)

```bash
java -jar companion/build/libs/drivepad-companion-1.0.0-all.jar
```

### Discover Active USB Tethering / LAN Interfaces

```bash
java -jar companion/build/libs/drivepad-companion-1.0.0-all.jar --list-interfaces
```

- **On Linux**: binds cleanly via `--interface usb0` or `--interface rndis0` (or IPv4 such as `192.168.42.10`).
- **On Windows**: binds cleanly via IPv4 (recommended, e.g., `192.168.42.10` or `0.0.0.0`) or friendly adapter display name substring (e.g., `--interface "Remote NDIS"` or `--interface "Ethernet 2"`).

### CLI Options

| Flag | Default | Description |
| :--- | :--- | :--- |
| `--backend`, `-B` | `noop` | `auto` (Linux `uinput` / Windows `vigem`), `noop`, `uinput` (Linux), `vigem` (Windows X360) |
| `--profile`, `-P` | `generic` | Controller profile (`generic`, `ets2`, or custom `.properties` ID) |
| `--port`, `-p` | `42100` | UDP listening port (`1..65535`) |
| `--interface`, `-i`, `--bind`, `-b` | `0.0.0.0` | Interface short name (`usb0`), Windows adapter display name (`Remote NDIS`), or IPv4 |
| `--list-interfaces` | — | List active non-loopback IPv4 interfaces and exit |
| `--list-profiles` | — | List available profiles and exit |
| `--watchdog-ms`, `-w` | `1500` | Failsafe neutral timeout in ms (`100..60000`) |
| `--device-name` | `DrivePad Virtual Gamepad` | Virtual device name label (Linux `uinput` max 79 UTF-8 bytes; XInput shows Xbox 360) |
| `--log-level`, `-l` | `INFO` | `ERROR`, `WARN`, `INFO`, or `DEBUG` |
| `--diagnostics`, `-d` | `DASHBOARD` | `DASHBOARD`, `LOGGING`, or `SILENT` |
| `--config`, `-c` | OS user config dir | Linux: `~/.config/drivepad-companion/config.properties`<br>Windows: `%APPDATA%\drivepad-companion\config.properties` |
| `--save-config` | `false` | Persist CLI flags into `config.properties` |
| `--check`, `--doctor` | — | Print version/protocol metadata, backend readiness, config paths & IPv4 interfaces and exit |
| `--version`, `-V` | — | Show DrivePad release version, Companion version, Protocol version & backend availability |
| `--help`, `-h` | — | Show CLI help |

---

## Windows 10 / 11 Virtual Gamepad Design & Setup

### Virtual Gamepad Driver Evaluation & Decision

| Option | Status | Driver Signing & Core Isolation (HVCI) | License | Steam Input / ETS2 Compatibility | Verdict |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **ViGEmBus (`v1.22.0`)** | Officially archived / EOL (Nov 2023; trademark dispute) | **Microsoft-signed** on Win 10/11; works with Secure Boot & HVCI enabled without Test Signing Mode | **BSD 3-Clause** | Emulates native **Xbox 360 (`XUSB`)** controller; 1:1 XInput + Steam Input + ETS2 trigger/axis support | **Selected (`:windows`)** with explicit EOL & external driver prerequisites |
| **vJoy (`njz3` / `shaul_ei`)** | Community fork; sporadic signing | Unreliable EV signing on newer Win 11 builds (`Code 52` under HVCI; older v2.1.9.1 workarounds) | Custom / FOSS mix | DirectInput only (`RX/RY` axis quirks in SDL/ETS2 without manual Steam remapping) | Rejected as primary backend |
| **VHF / UMDF2 (`libvirtualhid`)** | Modern user-mode HID framework | Requires proprietary/commercial VirtualHID driver distribution | Non-permissive / proprietary driver license | Good HID support once broker installed | Rejected due to licensing constraints |

### Windows Prerequisites (Required Only for Real Virtual Controller Injection on Windows)
DrivePad **never silently installs kernel drivers**. Before running `--backend vigem` or `--backend auto` on Windows 10 or Windows 11, manually install:

1. **Java 21+** (64-bit JDK/JRE on Windows x64, e.g., Eclipse Temurin 21).
2. **ViGEmBus Kernel Driver (`v1.22.0`, x64)**: Download the official signed `ViGEmBus_1.22.0_x64_x86_arm64.exe` installer from Nefarius (`nefarius/ViGEmBus` GitHub Releases) and run it with standard UAC approval.
   - *EOL Notice*: Nefarius officially retired/archived `ViGEmBus` in Nov 2023. Existing `v1.22.0` binaries are Microsoft-signed, so they generally load on Windows 10/11 without Test Signing Mode, but future Windows kernel / HVCI updates are not guaranteed to preserve compatibility. Do **not** disable Secure Boot or Core Isolation / HVCI as a default setup step.
3. **`ViGEmClient.dll` (64-bit)**: Place `ViGEmClient.dll` in one of DrivePad's probed paths:
   - Next to `drivepad-companion-1.0.0-all.jar` (current working directory)
   - `native\windows\ViGEmClient.dll`
   - `%APPDATA%\drivepad-companion\ViGEmClient.dll`
   - `C:\Program Files\Nefarius Software Solutions\ViGEmClient\ViGEmClient.dll`
   - Or anywhere on Windows `%PATH%` / `C:\Windows\System32\`
4. **Windows Defender Firewall (Wi-Fi & USB Tethering)**: On first UDP launch, allow **Java(TM) Platform SE binary** on **Private** networks for UDP port `42100`, or add an explicit inbound PowerShell rule:
   ```powershell
   New-NetFirewallRule -DisplayName "DrivePad UDP 42100" -Direction Inbound -Protocol UDP -LocalPort 42100 -Action Allow -Profile Private
   ```
5. **Driver Removal & Safe Recovery**: If `ViGEmBus` is uninstalled later via Windows Apps & Features / `devmgmt.msc`, DrivePad surfaces a clean diagnostic failure (`Driver/DLL unavailable`) on launch without crashing or hanging, and you can switch to `--backend noop` or bind another interface safely.

### Launching on Windows PowerShell

```powershell
# Inspect active IPv4 adapters (locate USB tethering / Remote NDIS IP)
java -jar companion\build\libs\drivepad-companion-1.0.0-all.jar --list-interfaces

# Run with Xbox 360 virtual gamepad and ETS2 profile
java -jar companion\build\libs\drivepad-companion-1.0.0-all.jar --backend vigem --profile ets2
```

### Windows XUSB Control Mapping (`ets2` & `generic`)

| DrivePad Input | Standard Code | Windows Xbox 360 `XUSB` Field | Notes |
| :--- | :--- | :--- | :--- |
| Steering Wheel / Tilt | `LEFT_X` (`ABS_X`) | `sThumbLX` (`-32768..32767`) | ETS2 `ets2`: `PROGRESSIVE` ($\gamma = 1.5$), `3%` deadzone |
| Accelerator (Digital) | `RIGHT_TRIGGER` (`ABS_Z`) | `bRightTrigger` (`0` or `255`) | Strictly digital (`0` or `255`) |
| Brake (Digital) | `LEFT_TRIGGER` (`ABS_RZ`) | `bLeftTrigger` (`0` or `255`) | Strictly digital (`0` or `255`) |
| Horn | `SOUTH` (`BTN_SOUTH`) | `XUSB_GAMEPAD_A` (`0x1000`) | `HOLD` in both `generic` & `ets2` |
| Handbrake | `EAST` (`BTN_EAST`) | `XUSB_GAMEPAD_B` (`0x2000`) | `PULSE_ON_CHANGE` (75 ms) in `ets2` |
| Lights | `NORTH` (`BTN_NORTH`) | `XUSB_GAMEPAD_X` (`0x4000`) | `PULSE_ON_CHANGE` (75 ms) in `ets2` |
| Wipers | `WEST` (`BTN_WEST`) | `XUSB_GAMEPAD_Y` (`0x8000`) | `PULSE_ON_CHANGE` (75 ms) in `ets2` |
| Left Indicator | `LEFT_SHOULDER` (`BTN_TL`) | `XUSB_GAMEPAD_LEFT_SHOULDER` (`0x0100`) | `PULSE_ON_CHANGE` (75 ms) in `ets2` |
| Right Indicator | `RIGHT_SHOULDER` (`BTN_TR`) | `XUSB_GAMEPAD_RIGHT_SHOULDER` (`0x0200`) | `PULSE_ON_CHANGE` (75 ms) in `ets2` |
| Hazard Lights | `BACK` (`BTN_SELECT`) | `XUSB_GAMEPAD_BACK` (`0x0020`) | `PULSE_ON_CHANGE` (75 ms) in `ets2` |
| Cruise Control | `START` (`BTN_START`) | `XUSB_GAMEPAD_START` (`0x0010`) | `PULSE_ON_CHANGE` (75 ms) in `ets2` |
| Engine | `GUIDE` (`BTN_MODE`) | `XUSB_GAMEPAD_GUIDE` (`0x0400`) | Remappable via `button.engine.code=LEFT_THUMB` if Xbox Game Bar captures Guide |
| Shift Up / Down | `DPAD_UP` / `DPAD_DOWN` | `XUSB_GAMEPAD_DPAD_UP` / `DOWN` | Sequential 75 ms shift pulses (`R <-> N <-> D`) |

*(Tip for Windows Xbox Game Bar: if Windows Game Bar intercepts `Guide`, either disable "Open Xbox Game Bar using Guide button" in Windows Settings or edit `%APPDATA%\drivepad-companion\profiles\ets2.properties` and set `button.engine.code=LEFT_THUMB`.)*

---

## Linux `/dev/uinput` Hardware Setup & Non-Root `udev` Guide

Do **not** run the DrivePad companion as `root` or under `sudo`. Grant unprivileged user access to `/dev/uinput` using a dedicated `uinput` group:

### 1. Kernel Module & `udev` Rule

```bash
sudo modprobe uinput
echo "uinput" | sudo tee /etc/modules-load.d/uinput.conf
```

Create `/etc/udev/rules.d/99-drivepad-uinput.rules`:

```udev
KERNEL=="uinput", SUBSYSTEM=="misc", GROUP="uinput", MODE="0660", OPTIONS+="static_node=uinput"
```

Apply the group and reload `udev` (log out and back in once so your active session inherits `uinput` group membership):

```bash
sudo groupadd -f uinput
sudo usermod -aG uinput "$USER"
sudo udevadm control --reload-rules
sudo udevadm trigger --name-match=uinput
# Verify non-root ownership and rw-rw---- permissions:
ls -l /dev/uinput
```

### 2. Firewall (Wi-Fi & USB Tethering)

- **UFW**: `sudo ufw allow 42100/udp`
- **firewalld**: `sudo firewall-cmd --add-port=42100/udp --permanent && sudo firewall-cmd --reload`

### 3. Launch on Linux

```bash
# Find USB tether interface (typically usb0, rndis0, or enx...)
java -jar companion/build/libs/drivepad-companion-1.0.0-all.jar --list-interfaces

# Run with ETS2 profile (non-root user in 'uinput' group)
java -jar companion/build/libs/drivepad-companion-1.0.0-all.jar \
  --backend uinput \
  --profile ets2 \
  --interface usb0
```

Legacy `.properties` keys (`ABS_X`, `ABS_Z`, `ABS_RZ`, `BTN_SOUTH`, `BTN_THUMBL`, etc.) remain 100% supported alongside canonical cross-platform keys (`LEFT_X`, `RIGHT_TRIGGER`, `LEFT_TRIGGER`, `SOUTH`, `LEFT_THUMB`, etc.).

---

## Verification Status & Honesty Matrix (Phase 11 & Phase 12)

For the complete hardware validation report, benchmark metrics, fault-injection findings, and physical device sign-off checklists, see [`../docs/PHASE_11_HARDWARE_VALIDATION_REPORT.md`](../docs/PHASE_11_HARDWARE_VALIDATION_REPORT.md).

| Component | Classification | Verification Scope |
| :--- | :--- | :--- |
| `:core` (33B UDP v1, watchdog failsafe, fast-HELLO reset, profile mapper, `ReleaseMetadata`, XDG + AppData paths) | **SIMULATED** | Automated JVM tests (`gradle -p companion :core:test`) |
| `:linux` (`UInputDevice`, LP64 `ioctl` ABI, missing `/dev/uinput` diagnostics, ETS2 pulses, failsafe resets) | **SIMULATED** / **BLOCKED** (Hardware) | Automated JVM tests with mock `InputEventWriter`; live `/dev/uinput`, `evtest`, Steam Input, ETS2 **BLOCKED** in sandbox |
| `:windows` (`XusbReportStruct` ABI, `WindowsHostProbe`, missing DLL/`ViGEmBus` diagnostics, `ViGEmX360GamepadBackend`) | **SIMULATED** / **BLOCKED** (Hardware) | Automated JVM tests on Linux JVM; live Win 10/11 + HVCI + `ViGEmBus` + `ViGEmClient.dll` **BLOCKED** in sandbox |
| Packaging, CLI & E2E Reliability (`ReleasePackagingAndDiagnosticsTest`, `ReliabilityAndSoakTest`, `ConfigAndCliTest`) | **SIMULATED** | Verified archive builds, `--version`/`--check` diagnostics, and reversible Linux install/uninstall scripts |
