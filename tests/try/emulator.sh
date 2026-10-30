#!/usr/bin/env bash
# Tries the app itself on an Android TV emulator (on GitHub, by .github/workflows/try.yml): installs it, opens it,
# closes the remote guide, goes into the website (its Sorani dialect), opens a film and presses play the way the
# remote does, with a screenshot (and the screen's UI tree) at every step; then the app's menu, which shows the
# web engine's version, and the log: the page's own errors, and any crash.
# Usage: tests/try/emulator.sh APK OUT_DIR NAME
set -uo pipefail
APK=$1
OUT=$2
NAME=$3
mkdir -p "$OUT"
n=0

shot() {
  n=$((n + 1))
  local f
  f=$(printf '%s/%s-%02d-%s' "$OUT" "$NAME" "$n" "$1")
  adb exec-out screencap -p > "$f.png"
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 && adb shell cat /sdcard/ui.xml > "$f.xml"
  echo "screenshot $f.png"
}

key() {
  for k in "$@"; do
    adb shell input keyevent "$k"
    sleep 1
  done
}

# Taps the middle of the first thing on the screen whose words match $1 (in the last screen's UI tree).
tap() {
  local xml at
  xml=$(ls -t "$OUT/$NAME"-*.xml 2> /dev/null | head -1)
  [ -n "$xml" ] || return 1
  at=$(python3 - "$1" "$xml" <<'PY'
import re, sys
pattern = re.compile(sys.argv[1], re.I)
xml = open(sys.argv[2], encoding="utf-8", errors="replace").read()
for node in re.findall(r"<node [^>]*>", xml):
    words = " ".join(re.findall(r'(?:text|content-desc)="([^"]*)"', node))
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
    if b and pattern.search(words):
        x1, y1, x2, y2 = map(int, b.groups())
        if x2 > x1 and y2 > y1:
            print((x1 + x2) // 2, (y1 + y2) // 2)
            break
PY
)
  if [ -z "$at" ]; then
    echo "nothing on the screen matches $1"
    return 1
  fi
  echo "tap $1 at $at"
  # shellcheck disable=SC2086  # x and y
  adb shell input tap $at
}

adb logcat -c
adb install -r "$APK" || exit 1
{
  echo "Android $(adb shell getprop ro.build.version.release) (API $(adb shell getprop ro.build.version.sdk))"
  adb shell dumpsys webviewupdate | grep -iE "current webview package|version" | head -5
} > "$OUT/$NAME-device.txt"
cat "$OUT/$NAME-device.txt"

adb shell am start -W -n com.twilightcafe.movies/.MainActivity
sleep 40
shot start
key KEYCODE_DPAD_CENTER   # the remote guide shows once, at the first start: its Close button has the remote
sleep 3
shot guide-closed
tap 'سۆرانی|sorani' || key KEYCODE_DPAD_DOWN KEYCODE_DPAD_CENTER
sleep 20
shot home
key KEYCODE_DPAD_CENTER   # what the website's TV interface has chosen first
sleep 15
shot film
tap 'پەخش|سەیر|تەماشا|play|watch' || key KEYCODE_DPAD_CENTER
sleep 30
shot play
sleep 30
shot later
key KEYCODE_MENU
sleep 3
shot menu
key KEYCODE_BACK

adb logcat -d > "$OUT/$NAME-logcat.txt"
grep -E "chromium|CONSOLE|AndroidRuntime|FATAL|twilightcafe|cr_" "$OUT/$NAME-logcat.txt" | tail -n 400 > "$OUT/$NAME-log.txt"
echo "done: $(ls "$OUT" | wc -l) files"
