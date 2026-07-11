#!/system/bin/sh
# clean-data-local-tmp.sh — clears /data/local/tmp EXCEPT /data/local/tmp/dev.perms.test
#
# Use-case:
#   - If prior installs/staging left junk folders under /data/local/tmp and you want to reclaim space.
#   - Preserves the app's own folder (/data/local/tmp/dev.perms.test) which may contain staged binaries.
#
# WARNING:
#   This deletes other apps' temp files under /data/local/tmp.
#   Only run this if you know what you're doing.

set -eu

BASE="/data/local/tmp"
KEEP="dev.perms.test"

log() { echo "[clean-tmp] $*" >&2; }

if [ ! -d "$BASE" ]; then
  log "Not found: $BASE"
  exit 0
fi

log "Cleaning $BASE (keeping $BASE/$KEEP)"

# If glob does not match, it stays as literal pattern. Guard with -e.
for p in "$BASE"/*; do
  [ -e "$p" ] || continue
  name="$(basename "$p")"
  [ "$name" = "$KEEP" ] && continue

  log "Removing: $p"
  # Directory or file—try rm -rf first (covers both on toybox/toolbox), then fallback.
  rm -rf "$p" 2>/dev/null || rm -f "$p" 2>/dev/null || true
done

log "Done."
