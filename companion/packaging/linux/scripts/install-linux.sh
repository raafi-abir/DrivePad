#!/usr/bin/env sh
set -eu

PREFIX="${HOME}/.local"
DRY_RUN=0

usage() {
    cat <<'EOF'
DrivePad Companion — Linux User Installer (Non-Root)
Usage: install-linux.sh [--prefix <dir>] [--dry-run] [--help]

Options:
  --prefix <dir>   Target installation prefix (default: ~/.local)
  --dry-run        Print planned actions without writing files
  --help           Show this help message
EOF
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --prefix)
            PREFIX="${2:?Missing directory after --prefix}"
            shift 2
            ;;
        --dry-run)
            DRY_RUN=1
            shift
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            usage >&2
            exit 1
            ;;
    esac
done

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
DIST_ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
SHARE_DIR="${PREFIX}/share/drivepad-companion"
BIN_DIR="${PREFIX}/bin"
CFG_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/drivepad-companion"

JAR_FILE=""
for f in "$DIST_ROOT/lib"/drivepad-companion-*-all.jar; do
    if [ -f "$f" ]; then
        JAR_FILE="$f"
        break
    fi
done

if [ -z "$JAR_FILE" ]; then
    echo "Error: Companion JAR not found under $DIST_ROOT/lib" >&2
    exit 1
fi

echo "Installing DrivePad Companion into prefix: $PREFIX"
if [ "$DRY_RUN" -eq 1 ]; then
    echo "[dry-run] mkdir -p \"$SHARE_DIR/lib\" \"$SHARE_DIR/udev\" \"$BIN_DIR\" \"$CFG_DIR/profiles\""
    echo "[dry-run] install -m 0644 \"$JAR_FILE\" \"$SHARE_DIR/lib/\""
    echo "[dry-run] install -m 0755 \"$DIST_ROOT/bin/drivepad-companion\" \"$BIN_DIR/drivepad-companion\""
    echo "[dry-run] install -m 0644 \"$DIST_ROOT/udev/99-drivepad-uinput.rules\" \"$SHARE_DIR/udev/\""
    exit 0
fi

if [ "$(id -u)" -eq 0 ]; then
    echo "Warning: install-linux.sh installs per-user binaries into \$HOME/.local; run as your normal desktop user, not root." >&2
fi

mkdir -p "$SHARE_DIR/lib" "$SHARE_DIR/udev" "$BIN_DIR" "$CFG_DIR/profiles"
chmod 0700 "$CFG_DIR" "$CFG_DIR/profiles"
cp -f "$JAR_FILE" "$SHARE_DIR/lib/"
chmod 0644 "$SHARE_DIR/lib/$(basename "$JAR_FILE")"

cp -f "$DIST_ROOT/udev/99-drivepad-uinput.rules" "$SHARE_DIR/udev/"
chmod 0644 "$SHARE_DIR/udev/99-drivepad-uinput.rules"
[ -f "$DIST_ROOT/README.txt" ] && cp -f "$DIST_ROOT/README.txt" "$SHARE_DIR/"
[ -f "$DIST_ROOT/THIRD_PARTY_NOTICES.md" ] && cp -f "$DIST_ROOT/THIRD_PARTY_NOTICES.md" "$SHARE_DIR/"

cat > "$BIN_DIR/drivepad-companion" <<EOF
#!/usr/bin/env sh
set -eu
JAR_PATH="$SHARE_DIR/lib/$(basename "$JAR_FILE")"
if ! command -v java >/dev/null 2>&1; then
    echo "Error: Java 21+ runtime not found in PATH." >&2
    exit 1
fi
exec java -jar "\$JAR_PATH" "\$@"
EOF
chmod 0755 "$BIN_DIR/drivepad-companion"

cat <<EOF

DrivePad Companion installed cleanly!
  Launcher : $BIN_DIR/drivepad-companion
  JAR      : $SHARE_DIR/lib/$(basename "$JAR_FILE")
  Config   : $CFG_DIR

Next steps for first-time Linux setup (non-root uinput):
  1. Ensure $BIN_DIR is in your PATH.
  2. Configure /dev/uinput access once (requires sudo):
       sudo modprobe uinput
       echo "uinput" | sudo tee /etc/modules-load.d/uinput.conf
       sudo cp "$SHARE_DIR/udev/99-drivepad-uinput.rules" /etc/udev/rules.d/
       sudo groupadd -f uinput
       sudo usermod -aG uinput "\$USER"
       sudo udevadm control --reload-rules && sudo udevadm trigger --name-match=uinput
     (Log out and back in so the 'uinput' group takes effect.)
  3. Allow UDP port 42100 in firewall (e.g., sudo ufw allow 42100/udp).
  4. Verify environment:
       $BIN_DIR/drivepad-companion --check
  5. Run with ETS2 profile:
       $BIN_DIR/drivepad-companion --backend uinput --profile ets2
EOF
