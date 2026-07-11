#!/system/bin/sh
# device-info.sh — quick device diagnostics (no root)
set -eu

echo "== Device Info =="
date || true
echo

echo "-- id --"
id || true
echo

echo "-- uname --"
uname -a || true
echo

echo "-- basic props --"
getprop ro.product.manufacturer 2>/dev/null || true
getprop ro.product.model 2>/dev/null || true
getprop ro.build.version.release 2>/dev/null || true
getprop ro.build.version.sdk 2>/dev/null || true
getprop ro.build.fingerprint 2>/dev/null || true
echo

echo "-- storage --"
df -h 2>/dev/null || df 2>/dev/null || true
echo

echo "-- pm/cmd paths --"
command -v pm 2>/dev/null || true
command -v cmd 2>/dev/null || true
command -v toybox 2>/dev/null || true
command -v busybox 2>/dev/null || true
command -v unzip 2>/dev/null || true
echo
