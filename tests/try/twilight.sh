#!/usr/bin/env bash
# The Twilight screens on the emulator (by try.yml, after emulator.sh; the app started again): chosen in the menu's
# Websites panel; their home as the websites' lists come in; the arrows across it; a film's page and BACK to the
# home (the remote on the same card again); a series' seasons and episodes; search with the screens' own letters;
# settings. A screenshot (and the screen's UI tree) at every step, and what the page said, in the log.
# Usage: tests/try/twilight.sh OUT_DIR NAME
set -uo pipefail
OUT=$1
NAME=$2-twilight
mkdir -p "$OUT"
n=0

# shot, key, tap, choose
# shellcheck source=tests/try/adb.sh
source "$(dirname "$0")/adb.sh"

adb logcat -c
adb logcat -v threadtime > "$OUT/$NAME-logcat.txt" 2> /dev/null &
logger=$!
finish() {
  kill "$logger" 2> /dev/null
  sleep 1
  grep -E "GeckoConsole|Twilight Movies|TwilightMovies|AndroidRuntime|FATAL" "$OUT/$NAME-logcat.txt" | tail -n 300 > "$OUT/$NAME-log.txt"
  echo "done: $(ls "$OUT" | grep -c "$NAME") files"
  exit 0
}

# The app afresh (the website's try before may have left its engine in a state of its own), then its menu
adb shell am force-stop com.twilightcafe.movies
adb shell am start -W -n com.twilightcafe.movies/.MainActivity
sleep 20
key KEYCODE_MENU
sleep 3
choose '^ماڵپەڕەکان|^Websites' || { key KEYCODE_MENU; sleep 3; choose '^ماڵپەڕەکان|^Websites'; } || finish
sleep 2
shot sites
choose '^توایڵایت|^Twilight' || { echo "no Twilight among the websites (the box's own engine?)"; key KEYCODE_BACK; finish; }
sleep 25   # every website's lists, the first time
shot home

# The arrows: down to the new films, on along the row (right to left), OK on one, BACK
key KEYCODE_DPAD_DOWN
shot row
key KEYCODE_DPAD_LEFT KEYCODE_DPAD_LEFT
shot row-on
key KEYCODE_DPAD_CENTER
sleep 10
shot film
key KEYCODE_DPAD_DOWN
shot film-down
key KEYCODE_BACK
sleep 6
shot back-home

# A series: its page, down to its episodes
key KEYCODE_DPAD_DOWN
shot series-row
key KEYCODE_DPAD_CENTER
sleep 10
shot series
key KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN
sleep 4
shot episodes
key KEYCODE_BACK
sleep 6
shot back-again

# Search: the bar's search, the English letters, "spider", what every website found
tap '^گەڕان$|^Search$' || key KEYCODE_DPAD_UP KEYCODE_DPAD_UP KEYCODE_DPAD_UP
sleep 3
shot search
tap '^ABC$' && sleep 2 && shot letters
for ch in s p i d e r; do tap "^$ch\$"; sleep 1; done
sleep 10
shot found

# Settings: its parts, the websites
tap '^ڕێکخستنەکان$|^Settings$'
sleep 3
shot settings
key KEYCODE_DPAD_DOWN KEYCODE_DPAD_CENTER
sleep 2
shot settings-sites
finish
