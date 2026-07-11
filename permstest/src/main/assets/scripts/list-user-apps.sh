#!/system/bin/sh
# list-user-apps.sh — list 3rd-party packages
set -eu

pm list packages -3 2>/dev/null | sed 's/^package://' | sort
