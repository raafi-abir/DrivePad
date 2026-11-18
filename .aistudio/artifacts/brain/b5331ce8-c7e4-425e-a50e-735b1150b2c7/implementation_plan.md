# DrivePad — Architecture Analysis & Technical Implementation Plan

DrivePad transforms an Android smartphone into a high-performance, low-latency virtual game controller specifically engineered for PC racing and heavy vehicle simulators, with primary focus on **Euro Truck Simulator 2 (ETS2)**.

---

## 1. Executive Summary & Concept Confirmation

### 1.1 Understanding of DrivePad
DrivePad addresses the core limitations of playing simulators like Euro Truck Simulator 2 without a dedicated $300+ physical wheel and pedal set. Keyboard steering lacks analog nuance, while standard gamepads have limited steering throw and lack cabin controls. DrivePad solves this by combining:
1. **High-Precision Analog Steering**: Through either an interactive on-screen wheel with degree indicators or a 6-axis gyroscope motion-steering engine with customizable calibration and sensitivity curves.
2. **Realistic Vehicle & Cabin Controls**: Integrated automatic (P/R/N/D) and manual (sequential Shift Up/Down with gear indicator) transmission layouts, analog throttle and brake pedals, handbrake, and dedicated cabin function pads (headlights, horn, hazard lights, windshield wipers, cruise control, engine start/stop, and turn indicators).
3. **Ultra-Low Latency Communication Pipeline**: Direct USB cable tethering using lightweight binary UDP packets targeting up to 250 Hz update rates with zero cloud dependencies.
4. **PC Companion & Virtual Gamepad Backend**: A companion daemon on Arch Linux translating UDP telemetry into kernel input events via `/dev/uinput`, exposing a native Xbox/SDL2-compatible controller and cabin keyboard bindings.

---

## 2. Technical Evaluation & Architecture Analysis

```
┌────────────────────────────────────────────────────────────────────────┐
│                   ANDROID CONTROLLER APPLICATION                       │
│                                                                        │
│  ┌───────────────────────┐  ┌───────────────────────────────────────┐  │
│  │   Jetpack Compose UI  │  │        Hardware SensorManager         │  │
│  │  - Cockpit Controls   │  │  - TYPE_GAME_ROTATION_VECTOR / Gyro   │  │
│  │  - Pedals & Steering  │  │  - Sampling at SENSOR_DELAY_FASTEST   │  │
│  │  - Gearbox & Toggles  │  │  - Auto-zero & Center Calibration     │  │
│  └───────────┬───────────┘  └───────────────────┬───────────────────┘  │
│              │                                  │                      │
│              ▼                                  ▼                      │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │            Input Normalizer & Math Pipeline (250 Hz)             │  │
│  │   - Non-linear response curves (Gamma/Exponential)               │  │
│  │   - Deadzone clamping & EMA Jitter Smoothing                     │  │
│  │   - Normalized Axis State: Steering [-1.0..1.0], Pedals [0.0..1.0] │
│  └──────────────────────────────────┬───────────────────────────────┘  │
│                                     ▼                                  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │           Binary Packet Encoder & UDP Transmit Engine            │  │
│  │   - 28-Byte Compact Binary Frame + CRC16 Checksum                │  │
│  │   - Non-blocking DatagramSocket over RNDIS/NCM Tether Interface   │  │
│  └──────────────────────────────────┬───────────────────────────────┘  │
└─────────────────────────────────────┼──────────────────────────────────┘
                                      │ USB Cable (USB Tethering)
                                      │ Point-to-Point UDP (~0.5ms RTT)
                                      ▼
┌────────────────────────────────────────────────────────────────────────┐
│                     LINUX PC COMPANION DAEMON                          │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                  UDP Receiver & Watchdog Service                 │  │
│  │   - Fixed-size DirectByteBuffer packet ingestion                 │  │
│  │   - Sequence check + Drop out-of-order packets                   │  │
│  │   - 60ms Fail-Safe Watchdog: Injects neutral state on disconnect │  │
│  └──────────────────────────────────┬───────────────────────────────┘  │
│                                     ▼                                  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │               Input Mapping & Virtualization Engine              │  │
│  │   - Scales normalized values to XInput/Linux EV_ABS ranges       │  │
│  │   - Maps cabin buttons to EV_KEY (Gamepad buttons / Keys)        │  │
│  └──────────────────────────────────┬───────────────────────────────┘  │
│                                     ▼                                  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                 Linux Virtual Device (/dev/uinput)               │  │
│  │   - Emulated Microsoft Xbox 360 Controller (VID:045e, PID:028e)  │  │
│  │   - Optional Hybrid Virtual Keyboard for extra ETS2 cabin hotkeys │  │
│  └──────────────────────────────────┬───────────────────────────────┘  │
└─────────────────────────────────────┼──────────────────────────────────┘
                                      ▼
┌────────────────────────────────────────────────────────────────────────┐
│             LINUX OS / STEAM INPUT / PROTON / GAMES                    │
│   - Euro Truck Simulator 2 (Native Linux or Steam Proton)              │
│   - Assetto Corsa / BeamNG.drive / Sim Racing Titles                   │
└────────────────────────────────────────────────────────────────────────┘
```

---

### 2.1 Communication Strategy: USB Tethering & UDP Protocol

#### How USB Tethering Operates
When Android enables USB Tethering, the Android kernel exposes a virtual network adapter to the PC (typically `rndis0` or `ncm0` under Linux).
- The Linux host automatically assigns a private point-to-point subnet (typically `192.168.42.0/24` with the phone at `192.168.42.129` or dynamic DHCP lease).
- **Latency Characteristics**: Round-trip time (RTT) on USB tethered point-to-point UDP is consistently **0.2ms – 0.6ms**, effectively matching native hardware USB polling rates.
- **Reliability**: Over a wired USB cable, physical frame loss is essentially 0%. UDP avoids TCP head-of-line blocking, TCP retransmission queues, and Nagle's algorithm delay.

#### Handshake and Dynamic Subnet Discovery
Subnet IPs can vary slightly depending on Android OEM implementations (e.g., `192.168.42.x`, `192.168.43.x`, or `192.168.50.x`).
- **Connection Handshake**:
  1. On launch, the Android app sends a broadcast ping to `255.255.255.255:<PORT>` with a `DRIVEPAD_DISCOVERY` magic header.
  2. The Linux companion listens on `<PORT>`, detects the phone's source IP, and sends a `DRIVEPAD_DISCOVERY_ACK` back.
  3. Both endpoints lock in direct unicast UDP. A heartbeat/keepalive frame maintains connection health.

#### Binary Packet Layout (28 Bytes Total)
To guarantee minimum overhead and memory allocation, a compact binary layout is enforced:

| Offset | Type | Field | Description | Range / Values |
|---|---|---|---|---|
| 0x00 | `uint16` | `magic` | Frame header identifier | `0x4450` ("DP" for DrivePad) |
| 0x02 | `uint8` | `version` | Protocol version | `0x01` |
| 0x03 | `uint8` | `flags` | Status flags | Bit 0: Motion Steering active; Bit 1: Calibrated |
| 0x04 | `uint32` | `sequence` | Incrementing frame counter | Detects dropped / out-of-order packets |
| 0x08 | `uint64` | `timestamp` | Monotonic clock (nanos) | Latency and jitter benchmarking |
| 0x10 | `int16` | `steering` | Analog Steering Axis | -32768 (Full Left) to +32767 (Full Right) |
| 0x12 | `uint16` | `throttle` | Analog Accelerator Pedal | 0 (Released) to 65535 (100% Depressed) |
| 0x14 | `uint16` | `brake` | Analog Brake Pedal | 0 (Released) to 65535 (100% Depressed) |
| 0x16 | `uint16` | `clutch` | Analog Clutch / Handbrake | 0 to 65535 |
| 0x18 | `uint8` | `gear_mode` | Mode & Selected Gear | High nibble: 0=Auto, 1=Manual. Low nibble: Gear (0=N, 1=R, 2=P, 3+=1st..12th) |
| 0x19 | `uint8` | `reserved` | Alignment padding | `0x00` |
| 0x1A | `uint16` | `buttons` | Digital button bitmask | Lights, Horn, Wipers, Hazard, Cruise, Engine, Blinker L/R |
| 0x1C | `uint16` | `crc16` | CCITT checksum | Data integrity validation |

---

### 2.2 Companion Application Platform: Evaluation of Kotlin/JVM vs. Native

| Factor | Kotlin/JVM (Selected for Dev) | C / Rust (Native Alternative) | Architectural Conclusion |
|---|---|---|---|
| **Code Sharing** | **100% Shared**: Packet encoder/decoder, mathematical curves, unit tests, and validation models can be shared across Android and PC in a common module. | Zero code sharing; duplicate models and protocols required. | Major efficiency win for Kotlin/JVM. |
| **Linux `uinput` Access** | Accessible via **JNA (Java Native Access)** or modern **Java 22+ FFM API (Foreign Function & Memory)** to call `ioctl` on `/dev/uinput`. | Direct POSIX `ioctl` calls without wrappers. | FFM and JNA both provide sub-microsecond `ioctl` dispatch overhead, negligible in a 4ms (250Hz) loop. |
| **GC Jitter & Latency** | Modern JVMs with ZGC/Shenandoah or zero-allocation buffers (pre-allocated `DirectByteBuffer`) avoid GC pauses during active game loops. | Zero GC pauses. | By adopting a **zero-allocation ring buffer** in Kotlin/JVM, GC pauses during steady-state packet processing are completely eliminated. |
| **Portability** | Multiplatform JVM runs on Arch Linux, Ubuntu, Fedora, and can later support Windows via ViGEmClient bindings. | Requires separate build toolchains for Linux and Windows. | Keeps the cross-platform roadmap straightforward. |

**Verdict**: Kotlin/JVM is well suited for the companion application, provided we strictly practice **zero runtime object allocation** in the 250 Hz UDP receiver loop (reusing a single fixed `ByteBuffer` and primitive struct fields).

---

### 2.3 Virtual Controller Subsystem: `uinput` vs. `UHID`

#### Linux `uinput` (`/dev/uinput`)
- **How it Works**: The Linux kernel `uinput` module allows user-space programs to create virtual input devices that emit kernel `input_event` structs (`EV_KEY`, `EV_ABS`, `EV_SYN`).
- **Game Engine & Steam Compatibility**: By declaring event codes matching an Xbox 360 controller (`BUS_USB`, Vendor `0x045e`, Product `0x028e`, with standard `ABS_X`, `ABS_Z`, `ABS_RZ`, `BTN_A`, `BTN_B`, etc.), the virtual device is automatically identified by `udev`, `SDL2`, `evdev`, Steam Input, and Proton as a standard game controller without extra drivers.
- **Permissions**: Easily accessible without root by creating a standard `/etc/udev/rules.d/99-uinput.rules` configuration:
  ```udev
  KERNEL=="uinput", MODE="0660", GROUP="input", TAG+="uaccess"
  ```
- **Simultaneous Gamepad & Keyboard Emulation**: `uinput` allows creating either a composite device or two coexisting virtual devices (one Gamepad + one Virtual Keyboard). This is critical for ETS2 because a standard gamepad only has ~12 buttons, whereas ETS2 has 30+ cabin controls (wipers, beacons, differential locks, retarder, engine start/stop).

#### Linux `UHID` (`/dev/uhid`)
- **How it Works**: Operates at the raw USB HID report descriptor level. Emulates the raw USB packet stream for devices like Sony DualShock or Logitech wheels.
- **Trade-offs**: Requires handcrafted binary HID report descriptors. Debugging descriptor syntax errors in kernel space is notoriously painful and brittle across kernel updates. It offers no performance advantages over `uinput` for analog stick and button emulation.

**Verdict**: **`uinput` is the clear, robust choice.** It is standard on Arch Linux, supported out of the box, provides immediate compatibility with Steam and ETS2, and supports hybrid gamepad + keyboard injection for complex cabin controls.

---

### 2.4 Critical Technical Challenges & Mitigation Strategies

1. **Watchdog & Fail-Safe Neutral State**:
   - *Risk*: If the user unplugs the USB cable, closes the app, or receives a phone call while at 100% throttle in a turn, the PC could hold the last received packet, causing an uncontrollable crash in ETS2.
   - *Mitigation*: The PC companion implements a high-resolution watchdog timer (50ms timeout). If no valid packet arrives within 50ms, the virtual gamepad immediately fires an event resetting steering to 0.0, throttle to 0%, releasing all buttons, and optionally engaging the parking brake.

2. **Sensor Sampling & Gyroscope Drift**:
   - *Risk*: Integrating raw gyroscope angular velocity (`TYPE_GYROSCOPE`) suffers from accumulated drift, causing the steering wheel to deviate from center over time.
   - *Mitigation*: We will use Android's fused `Sensor.TYPE_GAME_ROTATION_VECTOR` (which fuses gyroscope and accelerometer data without magnetic interference) to extract stable device roll angle in landscape mode. In addition, an instant "Recenter / Calibrate" tap target on the UI provides one-touch zero-point resetting.

3. **Multi-Touch Finger Tracking & Layout Ergonomics**:
   - *Risk*: A driver needs to hold the steering wheel with the left thumb while simultaneously feathering the throttle or brake with the right thumb, shifting gears, and tapping the turn indicator. Standard UI gesture detectors often intercept or cancel simultaneous pointers.
   - *Mitigation*: We will implement independent custom pointer-tracking via Compose `Modifier.pointerInput` with unique pointer ID tracking for each control zone (Wheel Zone, Pedal Zone, Gearbox Zone, Toggle Zone). No control will block or steal another control's multi-touch pointer.

4. **Android Power Management & Sleep Prevention**:
   - *Risk*: Android screen timeout or battery saver will dim the display, throttle the CPU, or freeze network sockets mid-drive.
   - *Mitigation*: Keep the driving screen awake using `android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON` on the Window, alongside a foreground execution state.

---

## 3. UI/UX Specification (Aligned with Visual Attachments)

The visual design directly reflects the user's reference designs (`image_51d835ab.png` and `image_831cd06f.png`):

### 3.1 Design Language & Palette
- **Background**: Deep graphite cockpit (`#0D0F12` to `#16191E`) with subtle mountain silhouette hero artwork.
- **Primary Accent (Racing Neon Green)**: `#00E676` / `#00C853` for active states, steering guide arc, accelerator pedal outline, and active gear indicators.
- **Secondary Accent (Braking Red)**: `#FF3D00` / `#D50000` for the brake pedal glowing bezel and brake telemetry bar.
- **Surfaces**: Dark glassmorphic panels (`#1A1D24` with subtle radial border gradients, inner bevel highlights, and 16.dp corner radius).

### 3.2 Landscape Screen Anatomy
1. **Top Bar**:
   - Top-Left: **Motion Steering Toggle** (Phone icon + "Motion Steering" pill switch).
   - Top-Left (adjacent): **Settings Gear** icon button (navigates to Settings).
2. **Left Flank (Steering Zone)**:
   - Sports steering wheel with textured leather rim, carbon-fiber spokes, D-pad aesthetic hub with glowing "D" emblem.
   - Dynamic overhead degree gauge showing real-time digital angle (`Steering 0.00` to `±1.00`) and green radial sweep indicator.
3. **Center Column (Telemetry & Safety)**:
   - Glass card with title **Controls** and gamepad icon:
     - Steering bar with dual-directional center-origin indicator (`-1.00` to `+1.00`).
     - Throttle bar with green fill (`0%` to `100%`).
     - Brake bar with red fill (`0%` to `100%`).
   - Bottom Center: Dedicated **Handbrake** rocker/toggle button with glowing white lever icon.
4. **Right Flank (Gearbox & Cabin Functions)**:
   - Header: Mode selector pill **[ Auto ] [ Manual ]**.
   - **Automatic Mode**: 4 large horizontal gear buttons `[ P ] [ R ] [ N ] [ D ]` (with active selection glowing bright neon green), followed by a 4x2 grid of cabin toggles:
     - Lights, Horn, Indicators (`<->`), Hazard
     - Wipers, Cruise, Engine, More (`...`)
   - **Manual Mode**: Large circular **Shift Up (⌃)**, center bracketed gear display `[ Gear 3 ]`, large circular **Shift Down (⌄)**, flanked by cabin toggles and directional turn indicator arrows `[ < ] [ > ]`.
5. **Bottom-Right Corner (Pedals)**:
   - Vertically inclined **Brake Pedal** (red ambient glow, slotted metal grip pads).
   - Vertically inclined **Accelerator Pedal** (neon green ambient glow, slotted metal grip pads).
   - Both pedals support continuous vertical drag for realistic pressure simulation or instant tap-to-floor.

---

## 4. Refined Phase-by-Phase Development Roadmap

To ensure zero regressions, reliable code quality, and verifiable milestones, the project will follow this exact progression:

### Phase 1: Android Foundation & Design System
- Establish core Android MVVM project architecture, landscape locking, and edge-to-edge support.
- Build theme tokens (`Color.kt`, `Type.kt`, `Theme.kt`) matching the neon-green/dark-graphite cockpit design language.
- Implement vector assets for pedals, steering wheel, transmission pads, and cabin icons.
- **Acceptance Criteria**: App compiles cleanly, launches in full-bleed landscape, displays cohesive dark racing theme with zero UI clipping or notch overlap.

### Phase 2: Main Controller Interface (Compose Cockpit)
- Implement interactive steering wheel with touch-rotation calculations (angle tracking, center snap-back).
- Implement vertical analog throttle and brake pedals with smooth thumb-drag and pressure feedback.
- Implement Gearbox switch between **Automatic (P/R/N/D)** and **Manual (Sequential Shift Up/Down with gear display)**.
- Implement cabin toggle buttons (Lights, Horn, Wipers, Hazard, Cruise, Engine, Indicators).
- Build the real-time "Controls" telemetry card showing instantaneous values.
- **Acceptance Criteria**: Complete multi-touch test passing (simultaneous steering + throttle drag + gear shifting without pointer loss); smooth 60fps animations.

### Phase 3: Sensor Processing & Motion Steering Engine
- Integrate Android `SensorManager` with `Sensor.TYPE_GAME_ROTATION_VECTOR` fallback to `TYPE_GYROSCOPE` + `TYPE_ACCELEROMETER`.
- Build math pipeline: roll angle extraction, sensitivity multiplier, non-linear response curves (gamma power), deadzone thresholding, and Exponential Moving Average (EMA) jitter filter.
- Implement auto-recenter calibration and the UI Motion Steering toggle.
- **Acceptance Criteria**: Tilting phone smoothly rotates on-screen wheel and telemetry bar; zero-point calibration resets neutral angle accurately within 0.5 degrees.

### Phase 4: Network & Binary Communication Engine
- Implement high-performance non-blocking UDP transmitter on Android.
- Implement binary packet encoder (28-byte frame with CRC16).
- Implement USB tethering network state detector and UDP discovery handshake.
- Add transmission telemetry (packet rate counter, dropped packets, RTT estimate).
- **Acceptance Criteria**: Continuous UDP packet transmission at up to 250 Hz with zero allocations in loop; validated against local loopback socket.

### Phase 5: PC Companion Daemon (Kotlin/JVM)
- Build the Linux PC companion CLI application in Kotlin/JVM.
- Implement asynchronous UDP receiver socket listening for DrivePad packets.
- Implement packet unpacker, sequence validation, and CRC verification.
- Implement connection watchdog timer (50ms fail-safe reset).
- **Acceptance Criteria**: Companion runs on Arch Linux, discovers phone on USB tether, receives 250 Hz stream, logs real-time jitter, and trips fail-safe cleanly upon cable disconnect.

### Phase 6: Linux Virtual Gamepad Integration (`uinput`)
- Implement `/dev/uinput` binding in Kotlin/JVM using JNA / POSIX bindings.
- Create virtual Xbox 360 gamepad exposing `ABS_X` (Steering), `ABS_Z` (Brake), `ABS_RZ` (Throttle), and controller buttons.
- Create virtual keyboard mapping for extended ETS2 cabin functions (e.g. 'E' for Engine, 'P' for Wipers, 'L' for Lights, '[' and ']' for Blinkers).
- Provide `/etc/udev/rules.d/99-uinput.rules` setup script.
- **Acceptance Criteria**: `jstest /dev/input/js0` and `evtest` confirm all axes and buttons respond accurately in real-time on Arch Linux without root.

### Phase 7: End-to-End Latency & Jitter Optimization
- Benchmark real-time round-trip latency across the physical USB tether connection.
- Measure packet delivery jitter over 100,000 packets.
- Tune buffer sizes and CPU thread priorities (`SCHED_FIFO` on companion where permitted).
- **Acceptance Criteria**: Average end-to-end latency < 4ms; 99th percentile jitter < 2ms; zero memory leaks.

### Phase 8: Euro Truck Simulator 2 Integration & Testing
- Configure ETS2 input wizard to bind DrivePad virtual axes and cabin switches.
- Test both Automatic (P/R/N/D) and Manual sequential shifting in-game.
- Verify smooth steering wheel behavior at 900-degree simulator scaling.
- Test Proton and native Linux ETS2 builds.
- **Acceptance Criteria**: Smooth driving, lane keeping, parking maneuvers, and full cabin operation in ETS2 without unhandled input glitches.

### Phase 9: Settings, Calibration & Profiles
- Implement Settings screen with DataStore persistence:
  - Steering sensitivity slider (0.1 to 3.0)
  - Steering deadzone adjustment (0% to 20%)
  - Gyroscope calibration offset and filtering strength
  - Custom button mapping manager
  - Gearbox presets (Reverse lock, auto-neutral on handbrake)
- **Acceptance Criteria**: All preferences persist across app restarts and immediately take effect in active driving sessions.

### Phase 10: Packaging, Polish & Documentation
- Final stability verification and stress testing (1-hour driving session).
- Arch Linux companion packaging (systemd service unit, install script, udev rules).
- User guide for enabling USB tethering and configuring ETS2 input bindings.
- **Acceptance Criteria**: Clean setup script for Arch Linux; reproducible installation on a clean system.

---

## 5. Decision Gates & Open Considerations

> [!IMPORTANT]
> **Decision 1: Extended Cabin Function Virtualization Strategy**
> A standard Xbox 360 controller via XInput only supports 10 digital buttons and 4 analog axes. ETS2 has ~30 cabin functions.
> - *Recommended Approach*: The Linux companion creates a **Composite Virtual Controller** (an Xbox 360 controller for Steering, Throttle, Brake, Shifting, plus a synchronized Virtual Keyboard emitting keystrokes like `E` for Engine, `P` for Wipers, `L` for Lights). This requires zero complex game modding.
>
> **Decision 2: Steering Return-to-Center Physics**
> When touch-steering is released:
> - *Default*: Smooth spring return-to-center animation (simulating wheel self-aligning torque).
> - *Configurable*: Option for instant snap-to-center or hold-last-angle in Settings.

---

## 6. Measurable Acceptance Criteria Summary

| Phase | Core Deliverable | Measurable Metric |
|---|---|---|
| **Phase 1** | Foundation & Theme | 0 build errors; 100% theme token coverage; edge-to-edge landscape support. |
| **Phase 2** | UI Cockpit Controls | 60+ FPS touch rendering; 100% multi-pointer isolation across pedals, wheel, and toggles. |
| **Phase 3** | Sensor Engine | Gyro roll angle mapped to [-1.0, 1.0] with < 0.5° drift post-calibration; EMA filter response < 10ms. |
| **Phase 4** | UDP Communication | 250 Hz packet rate verified on loopback; < 32-byte binary payload; 0 heap allocations per frame. |
| **Phase 5** | Linux Companion | Companion boots on Arch Linux; 50ms watchdog resets input to neutral upon link loss. |
| **Phase 6** | `uinput` Virtual Gamepad | Controller visible in `jstest-gtk` / Steam Input as Xbox 360 pad; full axis range [-32768, 32767]. |
| **Phase 7** | Latency & Jitter | Physical USB tether latency < 4ms average; 0 packet drops over 60-second burst. |
| **Phase 8** | ETS2 Playability | In-game truck steering, throttle, braking, and 8+ cabin functions operational in-game. |
| **Phase 9** | Settings & Profiles | DataStore persistence verified across app restart; dynamic tuning without connection drop. |
| **Phase 10** | Packaging & Docs | 1-step install script for Arch Linux companion with systemd service and udev rules. |
