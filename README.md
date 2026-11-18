# DrivePad — Android, Linux & Windows Cross-Platform Companion

DrivePad turns an Android smartphone into a low-latency steering wheel, digital pedal, gearbox, and customizable telemetry dashboard controller for PC simulators such as Euro Truck Simulator 2 (ETS2) over USB tethering or local Wi-Fi UDP.

The PC Companion uses a modular Gradle architecture (`companion/`):
- **`:core` (`companion/core/`)**: Shared 33-byte UDP protocol v1 codec, monotonic failsafe watchdog, connection/RTT/jitter diagnostics, OS-independent steering curves & toggle-to-pulse `ControllerProfile` engine, and `config.properties` persistence.
- **`:linux` (`companion/linux/`)**: Native Linux `/dev/uinput` virtual gamepad backend (`--backend uinput`).
- **`:windows` (`companion/windows/`)**: Native Windows Xbox 360 (`XUSB`) virtual gamepad backend via Nefarius `ViGEmBus` (`--backend vigem`).
- **Root `:companion` (`companion/`)**: Unified CLI launcher supporting `--backend auto` (Linux `uinput` / Windows `vigem`), `--backend noop`, and cross-platform fat JAR packaging.

For full Linux `/dev/uinput` setup, Windows `ViGEmBus` v1.22.0 evaluation & installation guide, USB tethering/Wi-Fi bind details, and ETS2 mappings, see [`companion/README.md`](companion/README.md).

---

## Phase 10: Advanced Dashboard Customization & Personalization

DrivePad's cockpit center telemetry panel is powered by a customizable Material 3 + Canvas gauge engine persisted in Android Jetpack DataStore.

### 1. Built-In Dashboard Layouts
Users can switch between four non-scrolling, landscape-adapted layouts in **Dashboard Studio** (accessible from the Home top bar, Home launch card, Cockpit top bar, or Controller Settings):

| Layout | Intended Driving Style | Included Regions & Elements |
| :--- | :--- | :--- |
| **Racing Dashboard** (Default) | High-focus performance & track/highway driving | Large Speedometer, large Circular/Digital RPM meter with redline arc, prominent Gear badge, active compact alerts |
| **Truck Dashboard** | ETS2 long-haul cabin overview | Balanced Speedometer & RPM meter, Gear badge, auxiliary sensor strip (shows `N/A` placeholders when external ETS2 telemetry isn't attached), full warning & vehicle function strip |
| **Minimal Dashboard** | Zero-distraction night/cruising view | Clean Speed, RPM, Gear, and critical warnings only |
| **Compact Dashboard** | Dense multi-value readout for smaller landscape phones | Tightened gauges, status indicators, and simultaneous telemetry strip |

### 2. Supported Gauge Customization
Supported gauges can be individually configured with:
- **Visibility** (Show / Hide)
- **Size** (`Small`, `Medium`, `Large`)
- **Presentation Style** (`Circular` needle arc vs `Digital` readout / segmented bar — where supported by the gauge)
- **Layout Region** (`Top Header`, `Main Stage`)
- **Label, Unit, and Limit/Redline Threshold Display**

Supported gauges backed by genuine DrivePad state:
- **Speedometer** (`km/h` or `mph` display conversion without mutating source values, selectable scale `160–320 km/h`, overspeed warning threshold)
- **RPM Meter** (Live engine RPM & max RPM scale, configurable redline warning threshold `3000–7800 RPM`, explicit `ENGINE OFF` state)
- **Gear Indicator** (Auto `P/R/N/D/1–6` or Manual `R/N/1–6`)

### 3. Theme, Visual Style & Performance Preferences
- **Color Theme**: `Dark`, `Light`, or `Follow System`
- **Accent Palette**: Racing Crimson, DrivePad Green, Telemetry Cyan, Amber Gold, Cobalt Blue
- **Gauge Card Style**: Carbon Gradient, Frosted Glass, High Contrast
- **Visual Density**: Compact or Spacious
- **Gauge Animations**: Smooth needle/color springs can be disabled for ultra-low-latency frame updates

### 4. Missing, Stale, Paused & Unavailable Telemetry Handling
- **Missing / Unavailable Data**: External ETS2 `scs-sdk-plugin` truck channels (`Fuel Level`, `Water Temperature`, `Oil Pressure`, `Air Pressure`, `Battery Voltage`, `AdBlue`, `Speed Limit`, and truck fault lights `ENG`, `TEMP`, `OIL`, `AIR`, `FUEL`) are currently **unavailable** from `CurrentDrivePadSource`.
  - Customization toggles for unavailable gauges are **strictly withheld** in Dashboard Studio so users are never offered non-functional settings.
  - Optional telemetry tiles render explicit `N/A` / `--` placeholders and are **never coerced to `0`**.
- **Stale / Disconnected / Paused States**: `DashboardStateFactory` monitors telemetry age (`> 2000 ms` timeout) and source status, displaying clear `STALE`, `NO LINK`, or `PAUSED` banners and `--` readouts rather than showing stale values as live.

### 5. Unified Persistence & Migration
`DashboardPreferencesRepositoryImpl` shares DrivePad's single `drivepad_user_preferences` DataStore (`UserPreferencesRepository.kt`) using namespaced `dashboard_*` keys, schema version `1`, defensive enum/range clamping, legacy `v0` gauge config migration, and a one-tap **Restore Default Dashboard Settings** action.

---

## Phase 11 & Phase 12: Packaging, Release & Hardware Validation Status

- **Android Application Identity**: Permanent package ID **`com.drivepad.app`** (`v1.0.0`, `versionCode = 1`, Protocol v1).
- **Installation, Packaging & Release Guide**: [`docs/RELEASE_AND_INSTALLATION_GUIDE.md`](docs/RELEASE_AND_INSTALLATION_GUIDE.md) (Android release signing via env vars, Linux `.tar.gz` + Arch `PKGBUILD` + non-root `udev`, Windows `.zip` + `ViGEmBus` EOL/HVCI disclosure, first-run walkthrough, and troubleshooting).
- **Third-Party Notices & Licensing**: [`docs/THIRD_PARTY_NOTICES.md`](docs/THIRD_PARTY_NOTICES.md) (AndroidX/Compose/Kotlin/Coroutines `Apache-2.0`, JNA `Apache-2.0`, and unbundled `ViGEmBus` `BSD-3-Clause` disclosures).
- **Final Security Audit & Release Readiness Report**: [`docs/FINAL_RELEASE_READINESS_REPORT.md`](docs/FINAL_RELEASE_READINESS_REPORT.md) (Threat model, Stage 4 security & fuzzing fixes, Arch Linux hardware guide, Protocol v2 roadmap, and release verdict).
- **Hardware Validation Report**: [`docs/PHASE_11_HARDWARE_VALIDATION_REPORT.md`](docs/PHASE_11_HARDWARE_VALIDATION_REPORT.md) (JVM loopback benchmark: **254.6 Hz / 0.245 ms avg RTT / 0.892 ms jitter / 0% loss**, Phase 11 bug fixes, and physical validation checklists).

---

## Known Limitations
- **Physical Hardware Checks Outstanding (`BLOCKED` in Sandbox)**: Live Linux `/dev/uinput` (`evtest`), Windows 10/11 `ViGEmBus` / `ViGEmClient.dll` / HVCI, physical Android USB tethering / Wi-Fi, Steam Input, and ETS2 game execution cannot run in the headless build container and have not yet been hardware-validated.
- **`ViGEmBus` Lifecycle**: Nefarius `ViGEmBus` (`v1.22.0`) is officially archived/EOL (Nov 2023); future Windows kernel/HVCI compatibility is not guaranteed upstream. DrivePad never silently installs drivers or disables HVCI/Secure Boot.
- **External ETS2 Truck Telemetry (`scs-sdk-plugin`)**: Live Fuel, Coolant Temp, Oil/Air Pressure, Battery, AdBlue, and Speed Limit channels remain `Unavailable` until a bi-directional telemetry plugin bridge is added.
- **No Physical Emulator/ADB in Build Environment**: Automated Compose UI and DataStore tests run via Robolectric on the JVM.

---

## Verification & Packaging Commands

Run Android unit + Robolectric Compose UI tests:
```bash
gradle :app:testDebugUnitTest --console=plain
```

Compile Android Release variant & build signed release APK (with keystore env vars):
```bash
gradle :app:compileReleaseKotlin --console=plain
# KEYSTORE_PATH=... STORE_PASSWORD=... KEY_PASSWORD=... gradle :app:assembleRelease --console=plain
```

Run all cross-platform companion tests and build standalone JAR + Linux `.tar.gz` + Windows `.zip` packages:
```bash
gradle -p companion test packageDistributions --console=plain
```
