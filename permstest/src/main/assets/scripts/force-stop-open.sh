#!/system/bin/sh
# force-stop-open.sh — force-stop then launch via monkey
set -eu

PKG="${1:-}"
if [ -z "$PKG" ]; then
  echo "Usage: force-stop-open.sh <package.name>" >&2
  exit 2
fi

echo "[*] force-stop $PKG"
am force-stop "$PKG" 2>/dev/null || true

echo "[*] launch $PKG"
monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
echo "[*] done"
