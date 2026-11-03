#!/usr/bin/env bash
# Studies the websites the Twilight screens can't read from GitHub's own machines yet, in the app's own engine on an
# emulator with a TV's screen (by .github/workflows/study.yml): a website opened from the Websites panel, its first
# page given time for Cloudflare's check, then down and OK twice (look); a page by its address (visit). Now: Kurd
# Subtitle's Watch, as the Twilight screens open it. A screenshot at each step (not the UI tree:
# reading it on a website's page froze the emulator). What their pages asked their data feeds, with the answers
# (assets/gecko/study.js, in this try build only, kept by the app), goes to NAME-data.txt after each page (if the
# emulator freezes on one, what came before stays). Kurdbin, Awena Film, Beenama, ShaFilm and Kurd Doblazh aren't
# studied: they show GitHub's machines a check instead (their pages come from the café's box).
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

# What the pages asked so far, with the answers, as the app keeps it (web/gecko/StudyFile: its own folder); not
# in place of what came before if the emulator gives nothing (frozen)
save() {
  timeout 30 adb shell cat /sdcard/Android/data/com.twilightcafe.movies/files/study.jsonl > "$OUT/data.new"
  if [ -s "$OUT/data.new" ]; then mv "$OUT/data.new" "$OUT/$NAME-data.txt"; else rm -f "$OUT/data.new"; fi
}

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
  save
}

# A website's page by its address (the menu's Website address, as emulator.sh opens one), its picture once it's there
visit() {
  key KEYCODE_MENU
  sleep 3
  choose '^ناونیشانی ماڵپەڕ$|^Website address$' || { key KEYCODE_BACK; return 1; }
  sleep 3
  adb shell input keyevent KEYCODE_MOVE_END
  # shellcheck disable=SC2046  # one key each
  adb shell input keyevent $(for i in $(seq 1 120); do printf 'KEYCODE_DEL '; done)
  adb shell input text "$2"
  sleep 2
  # the on-screen keyboard covers the panel (it takes the whole screen on a TV, and comes a moment late): it goes,
  # and Open is chosen
  for i in 1 2 3; do
    timeout 20 adb shell dumpsys input_method | grep -q "mInputShown=true" || break
    key KEYCODE_BACK
    sleep 1
  done
  shot "$1-address"
  tap '^کردنەوە$|^Open$' || key KEYCODE_DPAD_DOWN KEYCODE_DPAD_CENTER
  sleep 40
  picture "$1"
  save
}

look kurdfilm   # the website a new box opens
# Kurd Subtitle's Watch as the Twilight screens open it (this build takes these addresses as theirs): a film, then a
# series' episode (season 2, episode 3), its season and episode chosen on its page, then a free server (player-steps.js);
# a picture of each as it goes, and the page as drawn (player.js sends it, in this build)
visit subtitle-film 'https://kurdsubtitle.net/movies/779410-The-Paradise#tm-alone'
sleep 30
picture subtitle-film-later
visit subtitle-episode 'https://kurdsubtitle.net/tvshows/467300-Lupin#tm-play=2-3'
sleep 30
picture subtitle-episode-later
sleep 30
picture subtitle-episode-last
save

kill "$logger" 2> /dev/null
sleep 1
grep -E "GeckoConsole|TwilightMovies|AndroidRuntime|FATAL" "$OUT/$NAME-logcat.txt" | tail -n 400 > "$OUT/$NAME-log.txt"
echo "data: $(cat "$OUT/$NAME-data.txt" 2> /dev/null | wc -l) requests written down; $(ls "$OUT" | wc -l) files"
