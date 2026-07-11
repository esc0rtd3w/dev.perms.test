#!/system/bin/sh
# app-dump.sh — basic info for a package
set -eu

PKG="${1:-}"
if [ -z "$PKG" ]; then
  echo "Usage: app-dump.sh <package.name>" >&2
  exit 2
fi

echo "== $PKG =="
echo
echo "-- pm path --"
pm path "$PKG" 2>/dev/null || true
echo
echo "-- dumpsys package (header) --"
dumpsys package "$PKG" 2>/dev/null | sed -n '1,160p' || true
echo
echo "-- requested permissions --"
dumpsys package "$PKG" 2>/dev/null | sed -n '/requested permissions:/,/install permissions:/p' || true
