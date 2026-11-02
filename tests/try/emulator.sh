#!/usr/bin/env bash
# Tries the app itself on an emulator with a TV's screen (on GitHub, by .github/workflows/try.yml): installs it,
# opens it, closes the remote guide, goes into the website (its Sorani dialect), moves about it with the arrows (and
# searches with OK and the on-screen keyboard), opens a film, goes into its player and out with the arrows, and
# presses play the way the remote does, with a screenshot (and the screen's UI tree) at every step; then the app's
# menu, which shows the web engine and its version; Cloudflare's check (turnstile.html) in the app's engine; and
# the log: which engine the app chose, where the arrows went, the page's own messages, and any crash.
# Usage: tests/try/emulator.sh APK OUT_DIR NAME     (FILM, PLAY: what to tap, as patterns, if not KurdFilm's)
set -uo pipefail
APK=$1
OUT=$2
NAME=$3
mkdir -p "$OUT"
n=0

# shot, key, tap, choose
# shellcheck source=tests/try/adb.sh
source "$(dirname "$0")/adb.sh"

adb logcat -c
adb logcat -v threadtime > "$OUT/$NAME-logcat.txt" 2> /dev/null &   # all of it: the box's buffer is too small
logger=$!
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

# The arrows, from nothing chosen: the header, the search; OK on it opens the on-screen keyboard, the words go in,
# and OK searches
key KEYCODE_DPAD_DOWN
sleep 2
shot arrows-header
key KEYCODE_DPAD_DOWN
sleep 2
shot arrows-search
key KEYCODE_DPAD_CENTER
sleep 3
keyboard=$(adb shell dumpsys input_method | grep -c "mInputShown=true")
echo "on-screen keyboard after OK on the search: $([ "$keyboard" -gt 0 ] && echo shown || echo "not shown")" | tee -a "$OUT/$NAME-device.txt"
shot arrows-keyboard
adb shell input text "${SEARCH:-chicken}"
sleep 2
if adb shell dumpsys input_method | grep -q "mInputShown=true"; then key KEYCODE_BACK; fi   # the keyboard goes
shot arrows-typed
key KEYCODE_DPAD_CENTER
sleep 15
shot arrows-found
key KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN KEYCODE_DPAD_LEFT
sleep 2
shot arrows-films

tap "${FILM:-★}" || key KEYCODE_DPAD_CENTER   # a film (KurdFilm's posters say "★ 7.2")
sleep 20
shot film
# Into the film's player (a frame of the page) with the arrows, and out of it again
key KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN
sleep 3
shot arrows-player
key KEYCODE_DPAD_UP
sleep 3
shot arrows-out
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

# The same film with the ad blocker off for the website (the menu's switch: the page loads again), to see whether
# the blocker is what stops it
choose '^Ad blocker: On|^بلۆکەر|^ڕێگر' && sleep 30
shot no-blocker
key KEYCODE_DPAD_CENTER
sleep 40
shot no-blocker-later
key KEYCODE_MENU
sleep 3
choose '^Ad blocker: Off|^بلۆکەر|^ڕێگر' && sleep 10   # on again
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
choose '^ناونیشانی ماڵپەڕ$|^Website address$'
sleep 3
adb shell input keyevent KEYCODE_MOVE_END
# shellcheck disable=SC2046  # one key each
adb shell input keyevent $(for i in $(seq 1 80); do printf 'KEYCODE_DEL '; done)
adb shell input text "http://10.0.2.2:8765/turnstile.html"
sleep 1
# the on-screen keyboard covers the panel (it takes the whole screen on a TV): it goes, and Open is chosen
if adb shell dumpsys input_method | grep -q "mInputShown=true"; then key KEYCODE_BACK; fi
sleep 2
shot address
tap '^کردنەوە$|^Open$' || key KEYCODE_DPAD_DOWN KEYCODE_DPAD_CENTER
sleep 30
shot turnstile
kill "$server" 2> /dev/null

kill "$logger" 2> /dev/null
sleep 1
grep -E "chromium|CONSOLE|AndroidRuntime|FATAL|twilightcafe|TwilightMovies|cr_|GeckoConsole|Gecko[A-Z]|libc" "$OUT/$NAME-logcat.txt" \
  | tail -n 600 > "$OUT/$NAME-log.txt"
grep -m 3 "web engine:" "$OUT/$NAME-logcat.txt" | tee -a "$OUT/$NAME-device.txt"
grep -o "the remote: .*" "$OUT/$NAME-logcat.txt" | head -40 | tee -a "$OUT/$NAME-device.txt"
echo "done: $(ls "$OUT" | wc -l) files"
