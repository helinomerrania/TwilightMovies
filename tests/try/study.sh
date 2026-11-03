#!/usr/bin/env bash
# Studies the websites the Twilight screens can't read from GitHub's own machines yet, in the app's own engine on an
# emulator with a TV's screen (by .github/workflows/study.yml): each one opened from the Websites panel, its first
# page given time for Cloudflare's check, then down and OK twice (a film, its page), with a screenshot at each step
# (not the UI tree: reading it on a website's page froze the emulator). What every website's pages asked their data
# feeds, with the answers (assets/gecko/study.js, in this try build only, kept by the app), goes to NAME-data.txt,
# after each website (if the emulator freezes on one, what came before stays). Kurd Subtitle last: its page froze
# the emulator before. Awena Film, Beenama, ShaFilm and Kurd Doblazh aren't studied: they show GitHub's machines a
# check instead (their pages come from the café's box).
# Usage: tests/try/study.sh APK OUT_DIR NAME
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
adb logcat -v threadtime > "$OUT/$NAME-logcat.txt" 2> /dev/null &
logger=$!
adb install -r "$APK" || exit 1
adb shell am start -W -n com.twilightcafe.movies/.MainActivity
sleep 15
key KEYCODE_DPAD_CENTER   # the remote guide's Close (it shows at the first start)

# A website, as the app shows it: its first page, then down and OK twice
look() {
  sleep 45
  picture "$1-home"
  key KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN KEYCODE_DPAD_CENTER
  sleep 15
  picture "$1-next"
  key KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN KEYCODE_DPAD_CENTER
  sleep 15
  picture "$1-then"
  # What the pages asked so far, with the answers, as the app keeps it (web/gecko/StudyFile: its own folder); not
  # in place of what came before if the emulator gives nothing (frozen)
  timeout 30 adb shell cat /sdcard/Android/data/com.twilightcafe.movies/files/study.jsonl > "$OUT/data.new"
  if [ -s "$OUT/data.new" ]; then mv "$OUT/data.new" "$OUT/$NAME-data.txt"; else rm -f "$OUT/data.new"; fi
}

look kurdfilm   # the website a new box opens
for site in 'kurdbin:^Kurdbin' 'marscinema:^Mars Cinema' 'kurdsubtitle:^Kurd Subtitle'; do
  key KEYCODE_MENU
  sleep 3
  choose '^ماڵپەڕەکان|^Websites' || { key KEYCODE_BACK; continue; }
  sleep 2
  if choose "${site#*:}"; then look "${site%%:*}"; else key KEYCODE_BACK; fi
done

kill "$logger" 2> /dev/null
sleep 1
grep -E "GeckoConsole|TwilightMovies|AndroidRuntime|FATAL" "$OUT/$NAME-logcat.txt" | tail -n 400 > "$OUT/$NAME-log.txt"
echo "data: $(cat "$OUT/$NAME-data.txt" 2> /dev/null | wc -l) requests written down; $(ls "$OUT" | wc -l) files"
