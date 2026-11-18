# DrivePad Phase 11 — Cross-Platform Hardware Integration & Reliability Validation Report

**Date:** 2026-11-16  
**Protocol Version:** Protocol v1 (33-byte Little-Endian UDP, preserved identically across Android, Linux, and Windows)  
**Test Environment:** Headless Linux container (`Linux localhost 4.19.0-gvisor #1 SMP x86_64 GNU/Linux`), OpenJDK 21 / Kotlin JVM, Gradle Robolectric + JUnit4  

---

## Classification Key (Strict Honesty Policy)

Every item in this report is classified using strict factual criteria:

| Status | Meaning |
| :--- | :--- |
| **PASS** | Physically verified on real hardware (physical Android phone, real Linux `/dev/uinput` kernel, real Windows 10/11 host with `ViGEmBus`, Steam, and ETS2). |
| **FAIL** | Physically tested on real hardware and failed. |
| **BLOCKED** | Requires physical hardware, kernel driver, or host OS absent from this execution environment (`/dev/uinput`, `evtest`, physical Android USB tethering / Wi-Fi, Windows 10/11 + HVCI + `ViGEmBus` + `ViGEmClient.dll`, Steam, ETS2). |
| **SIMULATED** | Verified exclusively through automated JVM/Robolectric unit tests, loopback UDP sockets, mock writers/drivers, and fault injection in code. Never mislabeled as hardware `PASS`. |

### Container Environment Probe Summary
- Kernel: `4.19.0-gvisor` (gVisor sandbox — no `/dev/uinput`, no `/dev/input/*`, no `udevadm` daemon, no `evtest`/`jstest-gtk`/`sdl2-jstest`).
- Android Hardware / Emulator: No ADB device, no Android emulator, no physical USB RNDIS/tethering interface.
- Windows Host: Not present (`HostPlatform.current()` = Linux; no Windows 10/11 kernel, no `ViGEmBus.sys`, no `ViGEmClient.dll`, no `joy.cpl`, no Steam/ETS2 binary).
- **Automated Suites Verified After All Phase 11 Edits:**
  - Android JVM + Robolectric Compose UI: **40 / 40 `SIMULATED`** (`gradle :app:testDebugUnitTest --console=plain`)
  - PC Companion Multi-Module Suite (`:core` 26 + `:linux` 11 + `:windows` 5 + root E2E/reliability 13): **55 / 55 `SIMULATED`** (`gradle -p companion test --console=plain`)

---

## 1. Linux Hardware Validation Report

### 1.1 Verification Matrix

| Check Item | Physical Hardware Status | Automated / Simulated Status | Exact Code & Evidence |
| :--- | :---: | :---: | :--- |
| Companion fat JAR & distribution install (`fatJar`, `installDist`) | **BLOCKED** *(no target user PC)* | **SIMULATED** (Artifact built) | `gradle -p companion fatJar installDist` -> `drivepad-companion-1.0.0-all.jar` |
| Network interface selection & USB tethering (`--list-interfaces`, `--interface usb0`) | **BLOCKED** *(no physical `usb0`/`rndis0`)* | **SIMULATED** | `UdpServer.listUpIpv4Interfaces()`, `ConfigAndCliTest` (6/6) |
| UDP v1 connection from physical Android phone | **BLOCKED** *(no physical phone)* | **SIMULATED** | `CompanionEndToEndUdpTest`, `PlatformBackendEndToEndUdpTest` |
| Linux `/dev/uinput` virtual device creation (`ioctl` `UI_DEV_CREATE`) | **BLOCKED** *(gVisor has no `/dev/uinput`)* | **SIMULATED** | `UInputDevice` LP64 ABI struct tests (`GamepadMappingAndAbiTest`, `UInputDeviceTest`, 8/8) |
| Visibility in `evtest`, `jstest-gtk`, `sdl2-jstest` | **BLOCKED** | **BLOCKED** | Requires real Linux host evdev subsystem |
| Smooth steering axis behavior (`ABS_X`, ETS2 `PROGRESSIVE` $\gamma=1.5$, 3% deadzone) | **BLOCKED** *(live evdev)* | **SIMULATED** | `ProfileSystemAndMappingTest`, `GamepadMappingAndAbiTest`, `UInputGamepadBackendTest` |
| Digital throttle (`ABS_Z` `0..255`), brake (`ABS_RZ` `0..255`), handbrake (`BTN_EAST`) | **BLOCKED** *(live evdev)* | **SIMULATED** | `Ets2EndToEndBackendTest`, `UInputGamepadBackendTest` |
| Gear & ETS2 function buttons (`DPAD_UP/DOWN`, indicators, lights, wipers, horn, cruise, engine) | **BLOCKED** *(live evdev)* | **SIMULATED** | `Ets2EndToEndBackendTest` (75 ms pulse assertions) |
| Steam Input recognition | **BLOCKED** | **BLOCKED** | Requires Steam client on desktop Linux |
| ETS2 recognition & real truck control mapping | **BLOCKED** | **BLOCKED** | Requires Euro Truck Simulator 2 on desktop Linux |
| Watchdog failsafe when phone disconnects abruptly (1500 ms monotonic timeout) | **BLOCKED** *(live USB pull)* | **SIMULATED** | `WatchdogAndProtocolHandlerTest`, `PlatformBackendEndToEndUdpTest` |
| Safe neutral state (`ABS_X=0, ABS_Z=0, ABS_RZ=0`, all keys `0`) on companion shutdown | **BLOCKED** *(live kernel)* | **SIMULATED** | `DrivePadCompanionApp.stop()`, `UInputGamepadBackend.close()` |

### 1.2 Required Non-Root Linux `udev` Configuration
Never run `drivepad-companion` as `root` or via `sudo`. Configure `/dev/uinput` permissions once for your user account:

```bash
# 1. Load uinput module immediately and on boot
sudo modprobe uinput
echo "uinput" | sudo tee /etc/modules-load.d/uinput.conf

# 2. Create non-root group & udev rule (/etc/udev/rules.d/99-drivepad-uinput.rules)
sudo tee /etc/udev/rules.d/99-drivepad-uinput.rules <<'EOF'
KERNEL=="uinput", SUBSYSTEM=="misc", GROUP="uinput", MODE="0660", OPTIONS+="static_node=uinput"
EOF

# 3. Assign user to 'uinput' group and reload rules
sudo groupadd -f uinput
sudo usermod -aG uinput "$USER"
sudo udevadm control --reload-rules
sudo udevadm trigger --name-match=uinput
# Log out and back in so your shell session acquires the 'uinput' group.
```

### 1.3 Physical Linux Hardware Execution Runbook (For Sign-Off Machine)
1. Verify non-root permissions: `ls -l /dev/uinput` shows `crw-rw---- 1 root uinput ... /dev/uinput`.
2. Open UDP firewall if enabled: `sudo ufw allow 42100/udp` (or `firewall-cmd --add-port=42100/udp --permanent && sudo firewall-cmd --reload`).
3. Enable **USB tethering** on the Android phone and locate interface:
   ```bash
   java -jar companion/build/libs/drivepad-companion-1.0.0-all.jar --list-interfaces
   ```
4. Launch companion (unprivileged user):
   ```bash
   java -jar companion/build/libs/drivepad-companion-1.0.0-all.jar \
     --backend uinput --profile ets2 --interface usb0 --diagnostics LOGGING
   ```
5. Inspect virtual device in parallel terminal:
   ```bash
   sudo evtest   # Select "DrivePad Virtual Gamepad" and verify ABS_X (-32767..32767), ABS_Z/ABS_RZ (0..255), BTN_*
   sdl2-jstest --list
   ```
6. Launch Steam -> **Settings -> Controller -> Test Device Inputs**, verify Xbox/Gamepad layout, then launch **Euro Truck Simulator 2** -> **Options -> Controls** -> verify *Keyboard + DrivePad Virtual Gamepad* with *Real Automatic* or *Sequential* transmission.
7. While holding full throttle and steering in `evtest`, unplug the USB cable (or force-stop DrivePad): confirm `ABS_X=0, ABS_Z=0, ABS_RZ=0` within `~1.5 s` (`InputWatchdog`), then press `Ctrl+C` on companion and verify clean `SafeNeutral` & `UI_DEV_DESTROY`.

---

## 2. Windows Hardware Validation Report & Test Plan

### 2.1 Explicit `ViGEmBus` Retirement & Compatibility Disclosure
- **Dependency:** DrivePad's Windows backend (`:windows` / `--backend vigem`) uses **Nefarius `ViGEmBus` (`v1.22.0`)** via JNA FFI to `ViGEmClient.dll`, emulating a standard Microsoft Xbox 360 (`XUSB`) controller.
- **Retirement Disclosure:** Nefarius officially **archived/retired `ViGEmBus` in November 2023** following a trademark dispute. No upstream kernel maintenance or guaranteed compatibility with future Windows 11/12 kernel security changes exists.
- **No Silent Driver Install:** DrivePad never bundles, extracts, or silently installs kernel `.sys` drivers. Users must manually install the Microsoft-signed `ViGEmBus_1.22.0_x64_x86_arm64.exe` installer and place 64-bit `ViGEmClient.dll` in one of DrivePad's probed directories (`WindowsHostProbe.defaultDllSearchPaths()`).
- **HVCI / Memory Integrity Policy:** Do **not** disable Secure Boot or Windows Core Isolation / HVCI as a default setup instruction. Although `ViGEmBus v1.22.0` carries an attestation/WHQL signature from Microsoft's portal, HVCI behavior must be explicitly tested on real Windows 10 and Windows 11 builds (especially Win 11 23H2/24H2+).

### 2.2 Verification Matrix

| Check Item | Physical Hardware Status | Automated / Simulated Status | Notes & Code Coverage |
| :--- | :---: | :---: | :--- |
| Windows 10 (x64) host execution & JNA DLL load | **BLOCKED** | **BLOCKED** | Requires physical/VM Windows 10 x64 host |
| Windows 11 (x64) host execution & JNA DLL load | **BLOCKED** | **BLOCKED** | Requires physical/VM Windows 11 x64 host |
| `ViGEmBus v1.22.0` installation & `WindowsHostProbe` detection | **BLOCKED** | **SIMULATED** | `WindowsHostProbe.defaultDllSearchPaths()` path resolution tested in `WindowsViGEmBackendTest` |
| `ViGEmClient.dll` x64 JNA FFI struct ABI (`XusbReportStruct` 12-byte little-endian) | **BLOCKED** *(native call)* | **SIMULATED** | `WindowsViGEmBackendTest` verifies exact `sThumbLX`, `bLeftTrigger`, `bRightTrigger`, `wButtons` offsets/sizes |
| Xbox 360 (`XUSB`) virtual controller creation (`vigem_target_x360_alloc`) | **BLOCKED** | **SIMULATED** | `ViGEmX360GamepadBackend` + `PlatformBackendEndToEndUdpTest` with recording driver |
| Visibility in Windows `joy.cpl` (*Game Controllers*) | **BLOCKED** | **BLOCKED** | Requires Windows desktop |
| Steering, triggers & ETS2 button mapping (`ets2` profile) | **BLOCKED** | **SIMULATED** | `WindowsX360Mapping` verified against `ProfilePresets.ets2()` |
| Steam Input & ETS2 recognition on Windows | **BLOCKED** | **BLOCKED** | Requires Steam + ETS2 on Windows |
| Disconnect & `SafeNeutral` (`XusbReportData.NEUTRAL`) behavior | **BLOCKED** | **SIMULATED** | `ViGEmX360GamepadBackend.resetToSafeState()` + `ReliabilityAndSoakTest` |
| Driver removal / missing DLL recovery | **BLOCKED** | **SIMULATED** | `WindowsViGEmBackendTest` verifies non-crashing initialization failure + `lastError` diagnostic |
| HVCI / Memory Integrity compatibility test | **BLOCKED** | **BLOCKED** | Must be verified physically on Windows 11 with Memory Integrity ON |

### 2.3 Windows 10 & Windows 11 Physical Validation Runbook (For Sign-Off Machine)
1. **HVCI Pre-Check (Windows Security -> Device security -> Core isolation details):** Record whether **Memory integrity (HVCI)** is `On` or `Off` before testing. Keep it `On` first.
2. **Install Prerequisites:** Install 64-bit Temurin Java 21, run signed `ViGEmBus_1.22.0_x64_x86_arm64.exe` manually, copy 64-bit `ViGEmClient.dll` alongside `drivepad-companion-1.0.0-all.jar`.
3. **Firewall Rule (PowerShell):**
   ```powershell
   New-NetFirewallRule -DisplayName "DrivePad UDP 42100" -Direction Inbound -Protocol UDP -LocalPort 42100 -Action Allow -Profile Private
   ```
4. **Interface Discovery & Launch:**
   ```powershell
   java -jar companion\build\libs\drivepad-companion-1.0.0-all.jar --list-interfaces
   java -jar companion\build\libs\drivepad-companion-1.0.0-all.jar --backend vigem --profile ets2 --diagnostics LOGGING
   ```
   - Verify `Virtual Xbox 360 Device Active` and zero `UnsatisfiedLinkError` / JNA struct alignment faults.
5. **Windows Game Controller Verification (`Win+R` -> `joy.cpl`):**
   - Confirm `Controller (Xbox 360 For Windows)` appears -> **Properties** -> verify smooth X-axis sweep (`-32768..32767`), Z-axis triggers (`bRightTrigger` / `bLeftTrigger` `0` or `255`), D-pad shifts, and all face/shoulder buttons.
   - Verify Xbox Game Bar behavior if **Engine** (`GUIDE` / `0x0400`) triggers Windows overlay; test remapping `button.engine.code=LEFT_THUMB` in `%APPDATA%\drivepad-companion\profiles\ets2.properties`.
6. **Steam & ETS2 Test:** Confirm Xbox 360 controller recognition in Steam Input and ETS2 Controls wizard.
7. **Watchdog & Unload/Recovery Test:**
   - Unplug USB tether while holding throttle -> verify `joy.cpl` snaps back to neutral within `~1.5 s`.
   - Stop companion (`Ctrl+C`), uninstall `ViGEmBus` via **Apps & Features**, re-launch `--backend vigem`: confirm clean diagnostic error without JVM crash, then reinstall if continuing testing.

---

## 3. Android Connectivity Report

### 3.1 Verification Matrix

| Check Item | Physical Device Status | Automated / Simulated Status | Details |
| :--- | :---: | :---: | :--- |
| 33-byte Little-Endian Protocol v1 wire compatibility (`HELLO`..`DISCONNECT`) | **BLOCKED** *(real RF/USB link)* | **SIMULATED** | `PacketProtocolTest` + `Phase4NetworkingTest` verify byte-for-byte parity across Android `PacketCodec` and companion `PacketDecoder` |
| USB tethering route detection (`usb0`/`rndis0`/`ncm`/`enx*`) | **BLOCKED** | **SIMULATED** | `NetworkConnectivityMonitor.isUsbTetherInterfaceName()` + `Phase4NetworkingTest` |
| Cellular-only block (prevents sending UDP telemetry over mobile data) | **BLOCKED** | **SIMULATED** | `NetworkConnectivityMonitor.detectCellularOnlyActive()` verified |
| Local Wi-Fi UDP discovery/manual IP connection (`192.168.x.x:42100`) | **BLOCKED** *(no physical LAN phone)* | **SIMULATED** | UDP loopback tests verify `HELLO`/`HELLO_ACK`/`PING`/`PONG` |
| Watchdog recovery & local safety reset (`safetyResetAllInputs()`) | **BLOCKED** | **SIMULATED** | `Phase4NetworkingTest` verifies throttle/brake/horn snap to `0` on Watchdog timeout and Clean Disconnect |
| Android `onStop()` lifecycle safety reset | **BLOCKED** | **SIMULATED** | Implemented in `MainActivity.onStop()` (Phase 11 fix) |

### 3.2 Physical Android Connectivity Checklist
1. **USB Tethering Mode (Lowest Jitter — Recommended):**
   - Connect Android phone to PC via USB data cable -> **Settings -> Network & internet -> Hotspot & tethering -> USB tethering**.
   - On PC, run `--list-interfaces` to confirm the USB tether IPv4 (commonly `192.168.42.x` on Linux, `192.168.137.x` or `192.168.42.x` on Windows).
   - Enter that PC IPv4 in DrivePad **Network Settings**, tap **Test Connection** (`PING`/`PONG`), then **Start Telemetry** and rotate to landscape Cockpit.
2. **Local Wi-Fi Mode:**
   - Connect Android phone and PC to the same 5 GHz Wi-Fi SSID (avoid guest/AP-isolated networks).
   - Ensure PC firewall allows inbound UDP `42100` on Private/LAN profile.
   - Verify no carrier VPN/Private DNS interferes with local RFC1918 routing.
3. **Reconnect & Lifecycle Checks:**
   - Lock screen / press Home button mid-throttle -> `MainActivity.onStop()` resets local Android pedals/steering to neutral; companion enters `SafeNeutral` on `DISCONNECT` or watchdog expiry.
   - Reopen DrivePad and reconnect before `1.5 s` expires -> verify fresh `HELLO` resets any residual state before applying new steering/throttle frames.

---

## 4. Performance & Reliability Measurements

### 4.1 Measured JVM Localhost Loopback Benchmark (**SIMULATED**)
Measured by `ReliabilityAndSoakTest` (`companion/src/test/kotlin/com/drivepad/companion/ReliabilityAndSoakTest.kt`) transmitting **1,000 `CONTROLLER_STATE` packets** (sinusoidal steering + modulated digital throttle) + **20 `PING` RTT probes** (+1 `HELLO`) across real OS localhost UDP sockets (`127.0.0.1`) into the full `DrivePadCompanionApp` pipeline (`UdpServer` -> `PacketDecoder` -> `ProtocolHandler` -> `ControllerStateProcessor` -> `AsyncProfileWriterLoop` -> recording `UInputGamepadBackend`) over a 4-second 250 Hz window:

| Metric | Measured Value (`SIMULATED` Loopback) | Notes |
| :--- | :--- | :--- |
| Valid packets processed | **1,021** (`1 HELLO + 1000 STATE + 20 PING`) | Exact `ReliabilityAndSoakTest` run (`[PHASE11_BENCHMARK]`) |
| Sequence gaps / estimated packet loss | **0 gaps (`0.000%`)** | Zero packet drops over localhost UDP |
| Invalid / rejected packets | **0** | 100% clean Protocol v1 decode |
| Elapsed duration | **4.01 s** | Scheduled at `4 ms` intervals (250 Hz target) |
| Measured throughput (`actualHz`) | **254.6 Hz** | Meets 250 Hz target cleanly (includes 20 `PING` frames) |
| Client-measured RTT (avg / max) | **0.289 ms avg / 0.441 ms max** | Measured via echoed monotonic timestamps in `PING`/`PONG` |
| Server RFC3550 inter-arrival jitter | **0.451 ms** | Measured by `LatencyMonitor` in `:core` |
| Sender CPU time (`ThreadMXBean`) | **460 ms** over 4.01 s wall time | Modest JVM test-loop scheduling overhead |
| Heap delta (`totalMemory - freeMemory`) | **+942 KB** | Includes initial loopback buffers & `ArrayList` samples |

### 4.2 Fault-Injection Tests (**SIMULATED**)
1. **Fast Reconnect Before Watchdog Expiry (`ReliabilityAndSoakTest`):**
   - Client 1 sends `HELLO`, sets `steering = -0.9f, throttle = 1.0f`, then vanishes without sending `DISCONNECT` while `watchdogTimeoutMs = 5000L`.
   - Client 2 sends `HELLO` before watchdog expires.
   - **Verified:** Companion resets `ControllerStateProcessor` and `ViGEm` output immediately to `XusbReportData.NEUTRAL` (`SafeNeutral`) upon `HELLO`, preventing stuck throttle bleed across sessions.
2. **Mid-Stream Virtual Device Kernel I/O Failure (`ReliabilityAndSoakTest`):**
   - `InputEventWriter.writeEvents()` throws `IOException("Simulated /dev/uinput kernel failure")` mid-stream.
   - **Verified:** `backend.diagnostics().isVirtualDeviceActive` drops to `false`, `lastError` records the exception reason, and `udpServer.isRunning()` remains `true` without crashing the companion process.

### 4.3 Real-Hardware Physical Soak & Telemetry Collection (**BLOCKED**)
The following physical measurements **cannot** run in the headless gVisor container and remain **BLOCKED** pending physical Android + PC testing:
- **30-Minute Sustained Driving Session (USB Tether & 5 GHz Wi-Fi):**
  - Run companion with `--diagnostics LOGGING --log-level INFO` for 30 minutes while actively steering/pedaling in ETS2.
  - Expected thresholds to mark physical `PASS`: sustained `>= 200 Hz` over USB tether (`>= 180 Hz` on 5 GHz Wi-Fi), `< 0.5%` packet loss, `RTT < 5 ms` (USB) / `< 15 ms` (Wi-Fi), monotonic jitter `< 3 ms`, steady companion RSS `< 250 MB` (`ps -o pid,rss,%cpu -C java` on Linux or `Get-Process java` on Windows), zero phantom button releases or stuck inputs.

---

## 5. Bugs Identified & Fixed During Phase 11

| # | Bug Description | Root Cause | Fix Applied | Verification |
| :--- | :--- | :--- | :--- | :--- |
| 1 | **Stale active controller inputs survived across a fast phone reconnect** | When a phone crashed or restarted and sent a fresh `HELLO` *before* the companion's `1500 ms` `InputWatchdog` elapsed, `ProtocolHandler` accepted the new session without resetting `ControllerStateProcessor`, briefly leaving prior `throttle`/`steering`/flags active until the next `CONTROLLER_STATE` frame arrived. | Added guarded reset in `companion/core/src/main/kotlin/com/drivepad/companion/network/ProtocolHandler.kt` (`HELLO` branch): resets `stateProcessor.resetToFailsafe(nowNs)` whenever `!stateProcessor.controllerState.value.isSafeNeutral`. (Guards against duplicate failsafe callbacks when already neutral.) | **SIMULATED (`PASS` in `ReliabilityAndSoakTest` + `WatchdogAndProtocolHandlerTest`)** |
| 2 | **Local Android pedal/horn state remained latched after unexpected watchdog timeout or clean disconnect** | `ControllerConnectionManager.disconnectInternal()` and `triggerWatchdogOrHandshakeFailure()` stopped UDP TX/RX jobs without clearing `ControllerInputRepository`, leaving digital throttle/brake/horn/steering non-zero locally if the link dropped mid-press. | Called `inputRepository.safetyResetAllInputs()` inside both `disconnectInternal()` and `triggerWatchdogOrHandshakeFailure()` in `app/src/main/java/com/example/core/network/ControllerConnectionManager.kt`, plus added assertions in `Phase4NetworkingTest.kt`. | **SIMULATED (`PASS` in `Phase4NetworkingTest`)** |
| 3 | **Missing global background safety reset in `MainActivity.onStop()`** | Relying solely on screen-level disposal left a brief window when backgrounding the Android app via Home/Recents where input state reset could lag navigation lifecycle. | Overrode `onStop()` in `app/src/main/java/com/example/MainActivity.kt` to invoke `controllerInputRepository.safetyResetAllInputs()`. | **SIMULATED (`compile_applet` + Android unit suite 40/40)** |
| 4 | **Dashboard Studio layout overlap/collapse on compact `360 dp`-high landscape phones (discovered during Phase 10/11 verification)** | `DashboardCustomizationScreen` stacked a fixed `258.dp` live preview vertically over settings tabs on narrow landscape viewports (`w640dp-h360dp-land`), starving scroll space. | Switched `DashboardCustomizationScreen.kt` to side-by-side split pane (`maxWidth >= 560.dp`) with responsive `previewHeight` constraints. | **SIMULATED (`DashboardCustomizationUiTest` 2/2 on `w640dp-h360dp-land` + `w840dp-h480dp-land`)** |

---

## 6. Updated Installation & Operator Documentation

- Updated [`/companion/README.md`](../companion/README.md) with:
  - Explicit non-root Linux `udev` setup + `ls -l /dev/uinput` verification + `ufw`/`firewalld` UDP `42100` rules.
  - Explicit Windows 10/11 setup, `ViGEmBus v1.22.0` EOL & HVCI disclosure, `New-NetFirewallRule` command, driver removal/recovery behavior, and zero silent driver installs.
  - Updated Phase 11 Verification Status & Honesty Matrix (`55/55 SIMULATED` companion tests).
- Updated [`/README.md`](../README.md) with links to this validation report and updated verification commands.

---

## 7. Known Limitations

1. **No Real Hardware Execution in Build Container:** All Linux `/dev/uinput`, `evtest`, physical Android USB tethering/Wi-Fi, Windows 10/11 `ViGEmBus`, HVCI, Steam Input, and ETS2 game checks are `BLOCKED` here and require sign-off on real desktop/laptop hardware + a physical Android phone.
2. **`ViGEmBus` Is Archived / EOL:** The Windows `:windows` module depends on Nefarius `ViGEmBus` (`v1.22.0`), which is unmaintained upstream since November 2023. Long-term compatibility with future Windows kernel / HVCI updates cannot be guaranteed.
3. **External ETS2 Truck Telemetry (`scs-sdk-plugin`) Not Attached:** Fuel level, coolant temperature, oil pressure, air pressure, battery voltage, AdBlue, and speed limits remain `Unavailable` (`N/A` placeholders in `Truck` dashboard) until a bidirectional telemetry server bridge is added.
4. **Digital Pedals:** Current DrivePad Android pedals send digital `0.0f` / `1.0f` values as specified in Protocol v1 (mapped to `0..255` triggers cleanly without analog intermediate travel).
5. **Root Cause Guard for Xbox Game Bar:** On Windows, pressing `Engine` (`XUSB_GAMEPAD_GUIDE`) opens Xbox Game Bar unless disabled in Windows Settings or remapped in `ets2.properties` (`button.engine.code=LEFT_THUMB`).

---

## 8. Pre-Public-Release Checklist

- [ ] **Linux Real-Host Sign-Off:** Run `--backend uinput --profile ets2 --interface usb0` as non-root user (`uinput` group) on Ubuntu & Arch Linux; verify `evtest`, `sdl2-jstest`, Steam Input, ETS2 steering/shifter/indicators/wipers/cruise, and 1.5s watchdog neutral on cable yank.
- [ ] **Windows 10 & Windows 11 Sign-Off (With HVCI Enabled):** Verify manual `ViGEmBus v1.22.0` install, `ViGEmClient.dll` loading via JNA x64, `joy.cpl` Xbox 360 readout, Steam Input, ETS2 controls, Xbox Game Bar workaround, and clean recovery after uninstalling `ViGEmBus`.
- [ ] **Android Physical Device Validation:** Verify USB RNDIS/NCM tethering and 5 GHz Wi-Fi UDP connectivity on physical Android devices (API 29–35) against both Linux and Windows hosts.
- [ ] **30-Minute Physical Soak Benchmarks:** Execute 30-minute continuous driving sessions over USB tethering and Wi-Fi; record actual Hz, `< 0.5%` packet loss, RTT, jitter, and companion CPU/RSS footprint.
- [ ] **Android Release Signing & Packaging:** Configure release keystore signing outside debug builds, run ProGuard/R8 release shrinker verification, and test release APK (`assembleRelease`) on real device.
- [ ] **Optional Future Phase:** Implement bidirectional ETS2 `scs-sdk-plugin` telemetry bridge to populate currently unavailable truck dashboard channels (`Fuel`, `Coolant`, `Oil`, `Air`, `Speed Limit`).
