DrivePad Companion v1.0.0 — Linux Distribution
================================================

Requirements:
- 64-bit Linux (x86_64 or aarch64) with kernel uinput support
- Java Runtime Environment (JRE) 21 or newer

Quick Start:
1. Extract this archive and optionally install for your user account:
     ./scripts/install-linux.sh
   Or run directly from the extracted folder:
     ./bin/drivepad-companion --check

2. Configure non-root /dev/uinput permissions once:
     sudo modprobe uinput
     echo "uinput" | sudo tee /etc/modules-load.d/uinput.conf
     sudo cp udev/99-drivepad-uinput.rules /etc/udev/rules.d/
     sudo groupadd -f uinput
     sudo usermod -aG uinput "$USER"
     sudo udevadm control --reload-rules && sudo udevadm trigger --name-match=uinput
   Log out and back in so group membership takes effect. Never run as root.

3. Allow inbound UDP port 42100 in your firewall (if enabled):
     sudo ufw allow 42100/udp
   or:
     sudo firewall-cmd --add-port=42100/udp --permanent && sudo firewall-cmd --reload

4. Connect Android phone (USB tethering or same Wi-Fi LAN), list interfaces, and launch:
     ./bin/drivepad-companion --list-interfaces
     ./bin/drivepad-companion --backend uinput --profile ets2 --interface usb0

5. Press Ctrl+C to shut down safely (virtual gamepad automatically resets to neutral).

Uninstall:
  ./scripts/uninstall-linux.sh        (preserves ~/.config/drivepad-companion)
  ./scripts/uninstall-linux.sh --purge

Licensing:
  See THIRD_PARTY_NOTICES.md in this archive for Apache-2.0 and JNA disclosures.
