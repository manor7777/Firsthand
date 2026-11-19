#!/usr/bin/env bash
# Drives the debug app on a running Android emulator and saves screenshots. Run by the Screenshots workflow:
#   shoot.sh <debug.apk> [output dir]      (THEME=dark|light, default dark)
# It finds buttons by reading the screen's text (uiautomator), not by fixed coordinates, so layout tweaks don't break it.
set -uo pipefail
APK="$1"; OUT="${2:-docs/screenshots}"; PKG=app.firsthand; THEME="${THEME:-dark}"
here="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$OUT" /tmp/dumps
log() { echo "[shoot] $*"; }

dump()    { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1; }
find_xy() { dump; python3 "$here/find.py" /tmp/ui.xml "$@"; }
has()     { find_xy "$@" >/dev/null 2>&1; }
# Tap an element, retrying while the screen settles; keep the dump if it never appears (uploaded for debugging).
tap() {
  for _ in $(seq 1 12); do
    if xy=$(find_xy "$@"); then adb shell input tap $xy; log "tap $*"; sleep 2; return 0; fi
    sleep 1.5
  done
  log "NOT FOUND: $*"; cp /tmp/ui.xml "/tmp/dumps/notfound-$(echo "$*" | tr -c 'A-Za-z0-9' _).xml" 2>/dev/null; return 1
}
shot() { sleep "${2:-1.5}"; adb exec-out screencap -p > "$OUT/$1.png" && log "saved $1"; }
back() { adb shell input keyevent KEYCODE_BACK; sleep 2; }
size=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1); W=${size%x*}; H=${size#*x}
scroll_to() { for _ in $(seq 1 12); do has "$1" && return 0; adb shell input swipe $((W/2)) $((H*72/100)) $((W/2)) $((H*30/100)) 450; sleep 0.9; done; return 1; }

# --- set up: install, calm the system, clean status bar -------------------------------------------------------------
adb install -r "$APK" >/dev/null && log "installed"
for s in window_animation_scale transition_animation_scale animator_duration_scale; do adb shell settings put global $s 0; done
adb shell cmd uimode night $([ "$THEME" = light ] && echo no || echo yes)
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null
adb shell settings put global sysui_demo_allowed 1
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null 2>&1; }
demo enter; demo clock -e hhmm 1200; demo battery -e plugged false -e level 100; demo network -e wifi show -e level 4; demo notifications -e visible false

# --- the tour -------------------------------------------------------------------------------------------------------
adb shell am start -n "$PKG/.MainActivity" >/dev/null; log "launched; waiting for feeds"; sleep 20
shot 01-home 3

tap --card && { sleep 9; shot 02-article 2; back; }
tap "My News" && { sleep 8; shot 03-my-news 2; }

tap "Settings" && {
  tap "Sources"                          # Settings opens on General; the sources are on the second tab
  scroll_to "News sources" && { adb shell input swipe $((W/2)) $((H*60/100)) $((W/2)) $((H*45/100)) 300; sleep 1; shot 05-sources 1; }
  tap "Add a source" && { shot 06-add-source 1.5; tap "Mirror" && tap "Not now"; }
  back                                   # leave Settings
}

tap "My News" && {
  sleep 3
  # The section chips are a horizontally scrolling row: swipe it so the newly added source's chip is on screen.
  if xy=$(find_xy "All"); then y=${xy#* }; adb shell input swipe $((W*85/100)) "$y" $((W*10/100)) "$y" 300; sleep 1; fi
  tap "Mirror" && { sleep 10; shot 08-my-news-source 2; }
}
tap --card && { sleep 10; shot 09-article-source 2; }

demo exit
log "done: $(ls "$OUT"/*.png 2>/dev/null | wc -l) screenshots"
[ "$(ls "$OUT"/*.png 2>/dev/null | wc -l)" -ge 4 ]
