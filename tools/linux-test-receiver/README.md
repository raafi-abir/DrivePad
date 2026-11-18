# DrivePad Phase 4 — Temporary Arch Linux Test Receiver

This standalone Kotlin/JVM command-line utility verifies real-time UDP communication from the **DrivePad** Android app over USB tethering to an **Arch Linux** machine.

> **Scope Note**: This is a temporary Phase 4 test receiver only. It does **not** create a Linux `/dev/uinput` virtual gamepad or integrate with ETS2/Steam Input (those belong to Phase 5).

---

## 1. Prerequisites on Arch Linux

Install OpenJDK and the Kotlin compiler from official Arch repositories:

```bash
sudo pacman -S --needed jdk-openjdk kotlin
```

---

## 2. Compile & Run

From `/tools/linux-test-receiver`:

```bash
kotlinc DrivePadTestReceiver.kt -include-runtime -d DrivePadTestReceiver.jar
java -jar DrivePadTestReceiver.jar --port 42100 --bind 0.0.0.0
```

Optional flags:
- `--port <1..65535>` (default: `42100`)
- `--bind <ipv4>` (default: `0.0.0.0`, or bind directly to your USB tether IP such as `192.168.42.x` for tighter isolation)
- `--verbose` (prints rejected malformed packets and individual PING/PONG logs)

---

## 3. USB Tethering Network Setup (Android -> Arch Linux)

1. Connect the Android phone to your Arch Linux laptop using a USB data cable.
2. On Android, enable **Settings -> Network & internet -> Hotspot & tethering -> USB tethering**.
3. On Arch Linux, check the newly created USB network interface (`usb0`, `enp0s...`, or `enx...`) and note its local IPv4 address:

   ```bash
   ip -4 addr show
   ```

   *(Android USB tethering commonly assigns the laptop an address like `192.168.42.x` or `192.168.x.x` on a point-to-point subnet.)*

4. Allow incoming UDP packets on port `42100` if a firewall is active on Arch Linux:
   - **ufw**: `sudo ufw allow 42100/udp`
   - **firewalld**: `sudo firewall-cmd --add-port=42100/udp`
   - **nftables**: allow `udp dport 42100 accept` on the USB tether interface.
5. In the Android **DrivePad** app -> **Settings -> USB Tethering & UDP Receiver**:
   - Set **Receiver IPv4** to your Arch Linux USB tether IPv4 address.
   - Set **UDP Port** to `42100`.
   - Keep **Target Update Rate** at `250 Hz` (or adjust `20..500 Hz`).
   - Tap **Save Network Settings**, return to **Home** or **Telemetry & Diagnostics**, and tap **Connect PC Receiver**.

---

## 4. Binary Packet Format (33 Bytes, Little-Endian, Protocol v1)

| Offset | Size | Type | Field | Constraints |
|---|---|---|---|---|
| 0 | 1 | `UInt8` | `protocolVersion` | Must equal `1` |
| 1 | 1 | `UInt8` | `messageType` | `1=HELLO`, `2=HELLO_ACK`, `3=CONTROLLER_STATE`, `4=PING`, `5=PONG`, `6=DISCONNECT` |
| 2 | 4 | `UInt32` | `sequenceNumber` | Monotonic per sender |
| 6 | 8 | `Int64` | `monotonicTimestampNanos` | Monotonic clock nanos (`HELLO`/`PING` echoed in `HELLO_ACK`/`PONG` for RTT) |
| 14 | 4 | `Float32` | `steering` | `-1.0f .. +1.0f` |
| 18 | 4 | `Float32` | `throttle` | Strictly `0.0f` or `1.0f` |
| 22 | 4 | `Float32` | `brake` | Strictly `0.0f` or `1.0f` |
| 26 | 1 | `UInt8` | `handbrake` | `0` or `1` |
| 27 | 1 | `Int8` | `currentGear` | `-2` (`P`), `-1` (`R`), `0` (`N`), `1..12` |
| 28 | 1 | `UInt8` | `transmissionMode` | `0=AUTO`, `1=MANUAL` |
| 29 | 4 | `UInt32` | `vehicleButtonFlags` | Bitmask bits `0..7` (upper 24 bits must be `0`) |

---

## 5. Security Note

Raw UDP does **not** authenticate the sender. Any host able to reach the bound IP and UDP port can transmit packets. During Phase 4 testing, bind to your USB tethering interface IP (`--bind <usb-ip>`) or restrict firewall rules to the USB tether interface. Session tokens / sender verification will be addressed when building the Phase 5 companion.
