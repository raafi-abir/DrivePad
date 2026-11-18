#!/usr/bin/env sh
set -eu

PREFIX="${HOME}/.local"
PURGE=0
DRY_RUN=0

usage() {
    cat <<'EOF'
DrivePad Companion — Linux Uninstaller
Usage: uninstall-linux.sh [--prefix <dir>] [--purge] [--dry-run] [--help]

Options:
  --prefix <dir>   Target installation prefix (default: ~/.local)
  --purge          Also remove user config & profiles (~/.config/drivepad-companion)
  --dry-run        Print planned removals without deleting files
  --help           Show this help message
EOF
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --prefix)
            PREFIX="${2:?Missing directory after --prefix}"
            shift 2
            ;;
        --purge)
            PURGE=1
            shift
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

SHARE_DIR="${PREFIX}/share/drivepad-companion"
BIN_FILE="${PREFIX}/bin/drivepad-companion"
CFG_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/drivepad-companion"

if [ "$DRY_RUN" -eq 1 ]; then
    echo "[dry-run] rm -f \"$BIN_FILE\""
    echo "[dry-run] rm -rf \"$SHARE_DIR\""
    if [ "$PURGE" -eq 1 ]; then
        echo "[dry-run] rm -rf \"$CFG_DIR\""
    fi
    exit 0
fi

rm -f "$BIN_FILE"
rm -rf "$SHARE_DIR"

if [ "$PURGE" -eq 1 ]; then
    rm -rf "$CFG_DIR"
    echo "DrivePad Companion uninstalled and user config purged ($CFG_DIR)."
else
    echo "DrivePad Companion uninstalled from $PREFIX."
    echo "User config/profiles preserved at: $CFG_DIR (pass --purge to remove)."
fi
