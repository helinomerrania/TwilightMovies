#!/usr/bin/env bash
# Tries the app itself on an emulator with a TV's screen (on GitHub, by .github/workflows/try.yml): installs it,
# opens it, closes the remote guide, goes into the website (its Sorani dialect), opens a film and presses play the
# way the remote does, with a screenshot (and the screen's UI tree) at every step; then the app's menu, which
# shows the web engine and its version; Cloudflare's check (turnstile.html) in the app's engine; and the log:
# which engine the app chose, the page's own messages, and any crash.
# Usage: tests/try/emulator.sh APK OUT_DIR NAME     (FILM, PLAY: what to tap, as patterns, if not KurdFilm's)
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
    words = " ".join(w for w in re.findall(r'(?:text|content-desc)="([^"]*)"', node) if w)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
    if b and pattern.search(words):
        x1, y1, x2, y2 = map(int, b.groups())
        if x2 - x1 >= 20 and y2 - y1 >= 20:   # on the screen, not a hidden one
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
sleep 15
adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1   # so Firefox's engine shows its page to the UI tree too
sleep 25
shot start
key KEYCODE_DPAD_CENTER   # the remote guide shows once, at the first start: its Close button has the remote
sleep 3
shot guide-closed
tap 'سۆرانی|sorani' || key KEYCODE_DPAD_CENTER   # the website has Sorani chosen already
sleep 20
shot home
tap "${FILM:-★}" || key KEYCODE_DPAD_CENTER   # a film (KurdFilm's posters say "★ 7.2")
sleep 20
shot film
tap "${PLAY:-پەخش|سەیرکردن|سەیربکە|تەماشا|بینین|play|watch|▶}" || key KEYCODE_DPAD_CENTER
sleep 30
shot play
key KEYCODE_DPAD_CENTER   # OK on the player (play, if it's waiting for it)
sleep 30
shot later
sleep 30
shot later-still
key KEYCODE_MENU
sleep 3
shot menu
key KEYCODE_BACK
sleep 2

# Cloudflare's check (Turnstile) on a page of the test's own (turnstile.html, with Cloudflare's test key that always
# passes): KurdFilm's player needs it, and the café box's own engine couldn't run it. Opened as the website's
# address, typed in the menu (the emulator reaches this machine at 10.0.2.2).
python3 -m http.server 8765 --bind 0.0.0.0 --directory "$(dirname "$0")" > /dev/null 2>&1 &
server=$!
sleep 2
key KEYCODE_MENU
sleep 3
shot menu-again
tap '^ناونیشانی ماڵپەڕ$|^Website address$' || true
sleep 3
adb shell input keyevent KEYCODE_MOVE_END
# shellcheck disable=SC2046  # one key each
adb shell input keyevent $(for i in $(seq 1 80); do printf 'KEYCODE_DEL '; done)
adb shell input text "http://10.0.2.2:8765/turnstile.html"
sleep 1
shot address
tap '^کردنەوە$|^Open$' || key KEYCODE_ENTER
sleep 30
shot turnstile
kill "$server" 2> /dev/null

adb logcat -d > "$OUT/$NAME-logcat.txt"
grep -E "chromium|CONSOLE|AndroidRuntime|FATAL|twilightcafe|TwilightMovies|cr_|GeckoConsole|Gecko[A-Z]|libc" "$OUT/$NAME-logcat.txt" \
  | tail -n 600 > "$OUT/$NAME-log.txt"
grep -m 3 "web engine:" "$OUT/$NAME-logcat.txt" | tee -a "$OUT/$NAME-device.txt"
echo "done: $(ls "$OUT" | wc -l) files"
