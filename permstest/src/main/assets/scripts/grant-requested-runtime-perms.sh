#!/system/bin/sh
# grant-requested-runtime-perms.sh — grants requested runtime/dangerous permissions (best-effort)
set -eu

PKG="${1:-}"
if [ -z "$PKG" ]; then
  echo "Usage: grant-requested-runtime-perms.sh <package.name>" >&2
  exit 2
fi

echo "[*] Requested perms for $PKG:"
REQ="$(dumpsys package "$PKG" 2>/dev/null | sed -n '/requested permissions:/,/install permissions:/p' | sed -n 's/^[[:space:]]\{2,\}\([a-zA-Z0-9_.]*\)$/\1/p' | sort -u)"
if [ -z "$REQ" ]; then
  echo "[!] No requested permissions found (or dumpsys blocked)."
  exit 0
fi
echo "$REQ"
echo

echo "[*] Granting (best-effort)..."
OK=0
FAIL=0
echo "$REQ" | while IFS= read -r P; do
  [ -z "$P" ] && continue
  if pm grant "$PKG" "$P" 2>/dev/null; then
    echo "  [+] $P"
    OK=$((OK+1))
  else
    echo "  [-] $P"
    FAIL=$((FAIL+1))
  fi
done

echo
echo "[*] Done."
