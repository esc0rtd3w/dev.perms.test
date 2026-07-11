#!/system/bin/sh
# install-apk.sh — installs APK / APKM / APKS / XAPK (zip) using pm + unzip/toybox/busybox (auto-detect)
# No args: scan $PWD. If none found, fallback to script directory.
#
# Env options:
#   GRANT=1            -> add -g (grant runtime perms at install)
#   DOWNGRADE=1        -> add -d (allow downgrade)
#   INSTALL_USER=0     -> attempt install for user 0
#   OBB_DIR=/sdcard/Android/obb  -> override OBB destination
#   USE_ANDROID_DATA_STAGE=1     -> use app Android/data staging instead of /data/local/tmp
#   USE_STAGING_FOLDER=0         -> skip app imports staging and use the provided path directly
#   SKIP_STAGING_LARGE_FILES=1   -> skip app imports staging for files larger than 900 MB
#   BYPASS_LOW_TARGET_SDK_BLOCK=1 -> add --bypass-low-target-sdk-block on Android 15+
#   IGNORE_DEXOPT_PROFILE=1      -> add --ignore-dexopt-profile

set -eu


DEFAULT_PUBLIC_STAGE_BASE="/data/local/tmp/dev.perms.test/stage"
DEFAULT_PUBLIC_FILE_STAGE_BASE="/data/local/tmp/dev.perms.test/files"
DEFAULT_ANDROID_DATA_ROOT="/storage/emulated/0/Android/data/dev.perms.test/files"
LARGE_STAGE_SKIP_BYTES=943718400

if [ "${USE_ANDROID_DATA_STAGE:-0}" = "1" ]; then
  APP_STAGE_BASE="$DEFAULT_ANDROID_DATA_ROOT/stage"
  APP_FILE_STAGE_BASE="$DEFAULT_ANDROID_DATA_ROOT/imports"
else
  APP_STAGE_BASE="$DEFAULT_PUBLIC_STAGE_BASE"
  APP_FILE_STAGE_BASE="$DEFAULT_PUBLIC_FILE_STAGE_BASE"
fi

# Temp staging cleanup (ensures staged inputs/extracted archives don\'t accumulate junk even on failures)
TMP_DIRS=""
STAGED_INPUTS=""
cleanup_tmp() {
  for d in $TMP_DIRS; do
    [ -n "$d" ] || continue
    rm -rf "$d" 2>/dev/null || true
  done
  for f in $STAGED_INPUTS; do
    [ -n "$f" ] || continue
    rm -f "$f" 2>/dev/null || true
  done
}
trap cleanup_tmp EXIT INT TERM

# If the app bundles an unzip binary, it is staged here (shell-executable).
PUBLIC_BIN_DIR="/data/local/tmp/dev.perms.test/bin"

log() { echo "[install-apk] $*" >&2; }  # IMPORTANT: stderr, not stdout

need_cmd() { command -v "$1" >/dev/null 2>&1; }

filesize_bytes() {
  f="$1"
  if need_cmd stat; then stat -c %s "$f" 2>/dev/null && return 0; fi
  if need_cmd toybox; then toybox stat -c %s "$f" 2>/dev/null && return 0; fi
  wc -c < "$f" | tr -d ' '
}

has_install_multiple() { pm help 2>/dev/null | grep -q "install-multiple"; }

mktemp_dir() {
  base="$APP_STAGE_BASE"
  mkdir -p "$base" 2>/dev/null || true
  [ -w "$base" ] || base="/sdcard"
  d="$base/install_apk_tmp_$(date +%s)_$$"
  rm -rf "$d" 2>/dev/null || true
  mkdir -p "$d"
  TMP_DIRS="$TMP_DIRS $d"
  echo "$d"
}

stage_input_file() {
  src="$1"
  [ -f "$src" ] || { echo "$src"; return 0; }

  if [ "${USE_STAGING_FOLDER:-1}" != "1" ]; then
    echo "$src"
    return 0
  fi

  if [ "${SKIP_STAGING_LARGE_FILES:-0}" = "1" ]; then
    size="$(filesize_bytes "$src" 2>/dev/null || echo 0)"
    case "$size" in
      ""|*[!0-9]*) size=0 ;;
    esac
    if [ "$size" -gt "$LARGE_STAGE_SKIP_BYTES" ]; then
      log "Skipping input staging for large file: $src"
      echo "$src"
      return 0
    fi
  fi

  case "$src" in
    "$APP_FILE_STAGE_BASE"/*)
      echo "$src"
      return 0
      ;;
  esac

  base="$APP_FILE_STAGE_BASE"
  mkdir -p "$base" 2>/dev/null || true
  if [ ! -d "$base" ] || [ ! -w "$base" ]; then
    log "Input staging unavailable; using source path: $src"
    echo "$src"
    return 0
  fi

  name=$(basename "$src" | tr '
' '__')
  dst="$base/$(date +%s)_$$_$name"
  rm -f "$dst" 2>/dev/null || true
  if cp -f "$src" "$dst" 2>/dev/null || cat "$src" > "$dst"; then
    chmod 0644 "$dst" 2>/dev/null || true
    STAGED_INPUTS="$STAGED_INPUTS $dst"
    echo "$dst"
    return 0
  fi

  log "Input staging failed for: $src"
  echo "$src"
}

script_dir() {
  p="$0"
  case "$p" in
    /*) ;;
    *) p="$(pwd)/$p" ;;
  esac
  cd "$(dirname "$p")" 2>/dev/null && pwd
}

scan_dir_for_inputs() {
  d="$1"
  find "$d" -maxdepth 1 -type f \( \
    -name '*.apk' -o -name '*.apks' -o -name '*.apkm' -o -name '*.xapk' -o -name '*.zip' \
  \) 2>/dev/null | sort
}

unzip_any() {
  arch="$1"
  outdir="$2"

  # Try multiple common unzip implementations until one works.
  # Prefer system tools first; use staged/bundled unzip last.

  # toybox unzip (no -o flag)
  if need_cmd toybox; then
    toybox unzip -d "$outdir" "$arch" >/dev/null 2>&1 && return 0
  fi
  if [ -x /system/bin/toybox ]; then
    /system/bin/toybox unzip -d "$outdir" "$arch" >/dev/null 2>&1 && return 0
  fi

  # AOSP unzip (Info-ZIP) style
  if need_cmd unzip; then
    unzip -o "$arch" -d "$outdir" >/dev/null 2>&1 && return 0
  fi
  for p in \
    /system/bin/unzip \
    /system/xbin/unzip \
    /system_ext/bin/unzip \
    /vendor/bin/unzip \
    /product/bin/unzip \
    /odm/bin/unzip \
    /apex/com.android.runtime/bin/unzip \
    /apex/com.android.art/bin/unzip
  do
    if [ -x "$p" ]; then
      "$p" -o "$arch" -d "$outdir" >/dev/null 2>&1 && return 0
    fi
  done

  # busybox unzip (usually supports -o)
  if need_cmd busybox; then
    busybox unzip -o "$arch" -d "$outdir" >/dev/null 2>&1 && return 0
  fi
  for p in /system/bin/busybox /system/xbin/busybox /system_ext/bin/busybox /vendor/bin/busybox /product/bin/busybox /odm/bin/busybox; do
    if [ -x "$p" ]; then
      "$p" unzip -o "$arch" -d "$outdir" >/dev/null 2>&1 && return 0
    fi
  done

  # magisk busybox
  if need_cmd magisk; then
    magisk --busybox unzip -o "$arch" -d "$outdir" >/dev/null 2>&1 && return 0
  fi

  # tar-based fallbacks
  if need_cmd bsdtar; then
    bsdtar -xf "$arch" -C "$outdir" >/dev/null 2>&1 && return 0
  fi
  if need_cmd tar; then
    tar -xf "$arch" -C "$outdir" >/dev/null 2>&1 && return 0
  fi

  # Last resort: app-staged/bundled unzip.
  if [ -x "$PUBLIC_BIN_DIR/unzip" ]; then
    "$PUBLIC_BIN_DIR/unzip" -o "$arch" -d "$outdir" >/dev/null 2>&1 && return 0
    "$PUBLIC_BIN_DIR/unzip" "$arch" -d "$outdir" >/dev/null 2>&1 && return 0
    "$PUBLIC_BIN_DIR/unzip" -d "$outdir" "$arch" >/dev/null 2>&1 && return 0
  fi

  SCRIPT_DIR="$(cd "$(dirname "$0")" 2>/dev/null && pwd || echo "")"
  if [ -n "$SCRIPT_DIR" ] && [ -x "$SCRIPT_DIR/unzip" ]; then
    "$SCRIPT_DIR/unzip" -o "$arch" -d "$outdir" >/dev/null 2>&1 && return 0
    "$SCRIPT_DIR/unzip" "$arch" -d "$outdir" >/dev/null 2>&1 && return 0
    "$SCRIPT_DIR/unzip" -d "$outdir" "$arch" >/dev/null 2>&1 && return 0
  fi

  return 1
}

extract_archive() {
  arch="$1"
  outdir="$2"
  log "Extracting archive: $arch -> $outdir"
  unzip_any "$arch" "$outdir" || {
    log "unzip failed for: $arch"
    log "No working unzip found (tried: toybox unzip, unzip, busybox unzip, magisk --busybox unzip, bsdtar, tar)"
    exit 1
  }
}

copy_obbs_if_any() {
  src="$1"
  [ -d "$src/Android/obb" ] || return 0

  dest="${OBB_DIR:-}"
  if [ -z "$dest" ]; then
    if [ -d "/sdcard/Android/obb" ]; then
      dest="/sdcard/Android/obb"
    else
      log "OBB found, but /sdcard/Android/obb not found. Set OBB_DIR=..."
      return 1
    fi
  fi

  [ -d "$dest" ] || mkdir -p "$dest" 2>/dev/null || true

  log "Copying OBB(s) to: $dest"
  if need_cmd toybox; then
    toybox cp -a "$src/Android/obb/." "$dest/" 2>/dev/null || toybox cp -r "$src/Android/obb/." "$dest/"
  else
    cp -r "$src/Android/obb/." "$dest/"
  fi
  log "OBB copy done."
}


apk_basename() {
  p="$1"
  p="${p##*/}"
  echo "$p"
}

apk_config_qualifier() {
  b="$(apk_basename "$1")"
  case "$b" in
    split_config.*.apk)
      q="${b#split_config.}"
      q="${q%.apk}"
      ;;
    config.*.apk)
      q="${b#config.}"
      q="${q%.apk}"
      ;;
    *)
      echo ""
      return 0
      ;;
  esac

  # APK split names vary by store/exporter. Normalize common forms such as
  # config.arm64-v8a.apk, config.hdpi-v4.apk, and split_config.en-rUS.apk
  # so they are classified instead of being installed blindly as always-on splits.
  q="$(echo "$q" | sed 's/-/_/g; s/_v[0-9][0-9]*$//')"
  echo "$q"
}

is_split_config_apk() {
  b="$(apk_basename "$1")"
  case "$b" in
    split_config.*.apk|config.*.apk) return 0 ;;
  esac
  return 1
}

is_abi_qualifier() {
  q="$1"
  case "$q" in
    arm64_v8a|armeabi_v7a|armeabi|x86|x86_64|mips|mips64) return 0 ;;
  esac
  return 1
}

is_density_qualifier() {
  q="$1"
  case "$q" in
    ldpi|mdpi|tvdpi|hdpi|xhdpi|xxhdpi|xxxhdpi) return 0 ;;
  esac
  return 1
}

density_value() {
  case "$1" in
    ldpi) echo 120 ;;
    mdpi) echo 160 ;;
    tvdpi) echo 213 ;;
    hdpi) echo 240 ;;
    xhdpi) echo 320 ;;
    xxhdpi) echo 480 ;;
    xxxhdpi) echo 640 ;;
    *) echo 0 ;;
  esac
}

is_locale_qualifier() {
  q="$1"
  case "$q" in
    ??|???|??_r??|???_r??|??_??|???_??) return 0 ;;
  esac
  return 1
}

select_archive_apks_for_device() {
  input_list="$1"

  abi_raw="$(getprop ro.product.cpu.abilist 2>/dev/null || true)"
  [ -n "$abi_raw" ] || abi_raw="$(getprop ro.product.cpu.abi 2>/dev/null || true),$(getprop ro.product.cpu.abi2 2>/dev/null || true)"
  abi_prefs="$(echo "$abi_raw" | tr ',' ' ' | tr '-' '_')"

  locale_raw="$(getprop persist.sys.locale 2>/dev/null || true)"
  [ -n "$locale_raw" ] || locale_raw="$(getprop ro.product.locale 2>/dev/null || true)"
  lang="$(echo "$locale_raw" | sed 's/-/_/g' | cut -d_ -f1)"
  country="$(echo "$locale_raw" | sed 's/-/_/g' | cut -s -d_ -f2)"

  density="$(wm density 2>/dev/null | sed -n 's/.*[Dd]ensity: *\([0-9][0-9]*\).*/\1/p' | head -n 1)"
  [ -n "$density" ] || density="$(getprop ro.sf.lcd_density 2>/dev/null || true)"
  case "$density" in ""|*[!0-9]*) density=0 ;; esac

  base=""
  first=""
  always=""
  other_config=""
  pick_abi=""
  pick_abi_rank=999999
  pick_loc=""
  pick_den=""
  best_den_diff=999999

  while IFS= read -r p; do
    [ -n "$p" ] || continue
    [ -n "$first" ] || first="$p"
    b="$(apk_basename "$p")"
    if [ "$(echo "$b" | tr 'A-Z' 'a-z')" = "base.apk" ]; then
      base="$p"
      break
    fi
  done <<EOF_BASE_LIST
$input_list
EOF_BASE_LIST

  [ -n "$base" ] || base="$first"

  while IFS= read -r p; do
    [ -n "$p" ] || continue
    [ "$p" = "$base" ] && continue
    q="$(apk_config_qualifier "$p")"
    if [ -z "$q" ]; then
      always="${always}${always:+
}$p"
      continue
    fi

    if is_abi_qualifier "$q"; then
      rank=1
      for abi in $abi_prefs; do
        if [ "$q" = "$abi" ] && [ "$rank" -lt "$pick_abi_rank" ]; then
          pick_abi="$p"
          pick_abi_rank="$rank"
        fi
        rank=$((rank + 1))
      done
      continue
    fi

    if is_density_qualifier "$q"; then
      d="$(density_value "$q")"
      if [ "$density" -gt 0 ] && [ "$d" -gt 0 ]; then
        diff=$((density - d))
        [ "$diff" -lt 0 ] && diff=$((-diff))
        if [ "$diff" -lt "$best_den_diff" ]; then
          best_den_diff="$diff"
          pick_den="$p"
        fi
      fi
      continue
    fi

    if is_locale_qualifier "$q"; then
      q_lang="$(echo "$q" | cut -d_ -f1)"
      q_country="$(echo "$q" | sed -n 's/^[^_]*_r\{0,1\}\([^_]*\)$/\1/p')"
      if [ -n "$lang" ] && [ "$q_lang" = "$lang" ] && [ -z "$pick_loc" ]; then
        if [ -z "$country" ] || [ -z "$q_country" ] || [ "$q_country" = "$country" ]; then
          pick_loc="$p"
        fi
      fi
      continue
    fi

    if is_split_config_apk "$p"; then
      log "Skipping unsupported config split: $(apk_basename "$p")"
      continue
    fi

    other_config="${other_config}${other_config:+
}$p"
  done <<EOF_APK_LIST
$input_list
EOF_APK_LIST

  log "Split selection: base=$(apk_basename "$base") abi=$(apk_basename "$pick_abi") lang=$(apk_basename "$pick_loc") density=$(apk_basename "$pick_den")"

  {
    [ -n "$base" ] && echo "$base"
    [ -n "$always" ] && echo "$always"
    [ -n "$other_config" ] && echo "$other_config"
    [ -n "$pick_abi" ] && echo "$pick_abi"
    [ -n "$pick_loc" ] && echo "$pick_loc"
    [ -n "$pick_den" ] && echo "$pick_den"
  }
}

install_apk_files_session() {
  FLAGS="-r"
  [ "${GRANT:-0}" = "1" ] && FLAGS="$FLAGS -g"
  [ "${DOWNGRADE:-0}" = "1" ] && FLAGS="$FLAGS -d"
  if [ "${BYPASS_LOW_TARGET_SDK_BLOCK:-0}" = "1" ]; then
    sdk="$(getprop ro.build.version.sdk 2>/dev/null || echo 0)"
    case "$sdk" in
      ""|*[!0-9]*) sdk=0 ;;
    esac
    [ "$sdk" -ge 35 ] && FLAGS="$FLAGS --bypass-low-target-sdk-block"
  fi
  [ "${IGNORE_DEXOPT_PROFILE:-0}" = "1" ] && FLAGS="$FLAGS --ignore-dexopt-profile"

  if has_install_multiple; then
    log "Using: pm install-multiple"
    if [ -n "${INSTALL_USER:-}" ]; then
      pm install-multiple $FLAGS --user "$INSTALL_USER" "$@" 2>/dev/null && return 0
      log "install-multiple with --user failed; retrying without --user..."
    fi
    pm install-multiple $FLAGS "$@"
    return 0
  fi

  log "Using: pm install-create/install-write/install-commit"

  total_bytes=0
  for f in "$@"; do
    bytes=$(filesize_bytes "$f" 2>/dev/null || echo 0)
    case "$bytes" in
      ""|*[!0-9]*) bytes=0 ;;
    esac
    total_bytes=$((total_bytes + bytes))
  done
  [ "$total_bytes" -gt 0 ] || total_bytes=1048576

  CREATE_CMD="pm install-create -S $total_bytes $FLAGS"
  if [ -n "${INSTALL_USER:-}" ]; then
    if pm install-create -S "$total_bytes" $FLAGS --user "$INSTALL_USER" >/dev/null 2>&1; then
      CREATE_CMD="pm install-create -S $total_bytes $FLAGS --user $INSTALL_USER"
    else
      log "Note: --user not supported on install-create; ignoring INSTALL_USER=$INSTALL_USER"
    fi
  fi

  OUT=$($CREATE_CMD)
  ID=$(echo "$OUT" | awk -F'[][]' '{print $2}')
  [ -n "$ID" ] || { log "Could not parse session id from: $OUT"; return 1; }
  log "Session ID: $ID"

  for f in "$@"; do
    name=$(basename "$f")
    bytes=$(filesize_bytes "$f")
    log "Writing $name ($bytes bytes)"
    pm install-write -S "$bytes" "$ID" "$name" "$f"
  done

  log "Committing session..."
  pm install-commit "$ID"
}

# ---------- MAIN INPUT RESOLUTION ----------

INPUTS=""

if [ "$#" -eq 0 ]; then
  # Use $PWD as requested
  INPUTS="$(scan_dir_for_inputs "$PWD")"
  if [ -z "$INPUTS" ]; then
    # Fallback so `adb shell "sh /data/local/tmp/install-apk.sh"` works even when $PWD is /
    SDIR="$(script_dir)"
    log "No files found in \$PWD ($PWD). Falling back to script dir: $SDIR"
    INPUTS="$(scan_dir_for_inputs "$SDIR")"
  else
    log "No args: scanning current dir: $PWD"
  fi
elif [ "$#" -eq 1 ] && [ -d "$1" ]; then
  log "Scanning dir: $1"
  INPUTS="$(scan_dir_for_inputs "$1")"
else
  for f in "$@"; do
    [ -f "$f" ] || { log "Not a file: $f"; exit 1; }
    INPUTS="${INPUTS}${INPUTS:+
}$f"
  done
fi

[ -n "$INPUTS" ] || { log "No installable files found."; exit 1; }

log "Inputs found:"
echo "$INPUTS" | while IFS= read -r f; do log "  - $f"; done

# Split into APK vs archives
standalone_apks=""
archives=""

echo "$INPUTS" | while IFS= read -r f; do
  case "$(echo "$f" | tr 'A-Z' 'a-z')" in
    *.apk)  echo "APK:$f" ;;
    *.apks|*.apkm|*.xapk|*.zip) echo "ARC:$f" ;;
  esac
done | while IFS= read -r line; do
  kind="${line%%:*}"
  path="${line#*:}"
  if [ "$kind" = "APK" ]; then
    standalone_apks="${standalone_apks}${standalone_apks:+
}$path"
  else
    archives="${archives}${archives:+
}$path"
  fi

  # export to outer scope (POSIX sh limitation workaround by writing temp files)
  :
done

# POSIX sh subshell scope sucks; re-derive lists without pipe subshells:
standalone_apks="$(echo "$INPUTS" | awk 'tolower($0) ~ /\.apk$/ {print}')"
archives="$(echo "$INPUTS" | awk 'tolower($0) ~ /\.(apks|apkm|xapk|zip)$/ {print}')"

# Archives: extract+install each
if [ -n "$archives" ]; then
  echo "$archives" | while IFS= read -r a; do
    [ -n "$a" ] || continue
    staged_archive="$(stage_input_file "$a")"
    tmp="$(mktemp_dir)"
    extract_archive "$staged_archive" "$tmp"

    apks="$(find "$tmp" -type f -name '*.apk' 2>/dev/null | sort)"
    if [ -z "$apks" ]; then
      log "No APKs found inside archive: $a"
      rm -rf "$tmp" 2>/dev/null || true
      continue
    fi

    copy_obbs_if_any "$tmp" || log "OBB copy skipped/failed (may still install APKs)."

    selected_apks="$(select_archive_apks_for_device "$apks")"
    if [ -z "$selected_apks" ]; then
      log "No APKs selected for archive: $a"
      rm -rf "$tmp" 2>/dev/null || true
      continue
    fi

    log "Installing selected APKs from archive: $a"
    echo "$selected_apks" | while IFS= read -r sf; do
      [ -n "$sf" ] || continue
      log "  - $(basename "$sf")"
    done
    # shellcheck disable=SC2086
    install_apk_files_session $selected_apks

    rm -rf "$tmp" 2>/dev/null || true
  done
fi

# Standalone APKs: install one-by-one (so multiple APKs in folder don’t get treated as splits)
if [ -n "$standalone_apks" ]; then
  echo "$standalone_apks" | while IFS= read -r s; do
    [ -n "$s" ] || continue
    staged_apk="$(stage_input_file "$s")"
    log "Installing standalone APK: $s"
    install_apk_files_session "$staged_apk"
  done
fi

log "All done."
