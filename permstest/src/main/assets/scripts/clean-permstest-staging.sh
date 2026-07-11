#!/system/bin/sh
# clean-permstest-staging.sh — clears ONLY PermsTest staging folders under /data/local/tmp/dev.perms.test
#
# Safe cleanup for normal use:
#   - Removes archive/split install staging dirs created by this app.
#   - Preserves installed bins/tools under /data/local/tmp/dev.perms.test/bin.
#
# This does NOT wipe /data/local/tmp. For that, use clean-data-local-tmp.sh.

set -eu

BASE="/data/local/tmp/dev.perms.test"
FILES="$BASE/files"

log() { echo "[clean-permstest-staging] $*" >&2; }

if [ ! -d "$BASE" ]; then
  log "Nothing to clean (missing: $BASE)"
  exit 0
fi

# Known staging locations used by the app and scripts.
STAGE1="$BASE/stage"
STAGE2="$BASE/bin/stage"

ALL=0
for a in "$@"; do
  case "$a" in
    --all) ALL=1 ;;
  esac
done

clean_stage() {
  p="$1"
  [ -d "$p" ] || return 0
  log "Cleaning: $p"
  # Remove only known temporary patterns
  rm -rf "$p"/pkg_* 2>/dev/null || true
  rm -rf "$p"/install_apk_tmp_* 2>/dev/null || true
}

clean_stage "$STAGE1"
clean_stage "$STAGE2"

if [ -d "$FILES" ]; then
  log "Cleaning: $FILES"
  rm -rf "$FILES"/* 2>/dev/null || true
fi

if [ "$ALL" -eq 1 ]; then
  log "--all specified: wiping $BASE (including bin/tools)"
  # Busybox/toybox-safe wipe without fragile globs.
  find "$BASE" -mindepth 1 -maxdepth 1 -exec rm -rf {} + 2>/dev/null || true
fi

log "Done."
