#!/usr/bin/env bash
# Studies the websites the Twilight screens can't read from GitHub's own machines yet, in the app's own engine on an
# emulator with a TV's screen (by .github/workflows/study.yml): each one opened from the Websites panel (in the
# panel's order: the remote goes down it), its first page given time for Cloudflare's check, then down and OK twice
# (a film, its page), with a screenshot (and the UI tree) at each step. What every website's pages asked their data
# feeds, with the answers' start (assets/gecko/study.js, in this try build only), goes to NAME-data.txt.
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
  shot "$1-home"
  key KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN KEYCODE_DPAD_CENTER
  sleep 15
  shot "$1-next"
  key KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN KEYCODE_DPAD_CENTER
  sleep 15
  shot "$1-then"
}

look kurdfilm   # the website a new box opens
for site in 'kurdsubtitle:^Kurd Subtitle' 'kurddoblazh:^کورد دۆبلاژ|^Kurd Doblazh' 'awenafilm:^Awena Film' \
            'kurdbin:^Kurdbin' 'marscinema:^Mars Cinema' 'beenama:^Beenama' 'shafilm:^ShaFilm'; do
  key KEYCODE_MENU
  sleep 3
  choose '^ماڵپەڕەکان|^Websites' || { key KEYCODE_BACK; continue; }
  sleep 2
  if choose "${site#*:}"; then look "${site%%:*}"; else key KEYCODE_BACK; fi
done

kill "$logger" 2> /dev/null
sleep 1
# Each request with its answer whole again (the log has it in pieces: study.js), one JSON line each
python3 - "$OUT/$NAME-logcat.txt" > "$OUT/$NAME-data.txt" <<'PY'
import json, re, sys
heads, pieces = {}, {}
for line in open(sys.argv[1], encoding='utf-8', errors='replace'):
    m = re.search(r'Twilight Movies, data (\d+)(?:\.(\d+))?: (.*)$', line.rstrip('\n'))
    if not m:
        continue
    if m.group(2) is None:
        try:
            heads[int(m.group(1))] = json.loads(m.group(3))
        except ValueError:
            pass
    else:
        pieces.setdefault(int(m.group(1)), {})[int(m.group(2))] = m.group(3)
for n in sorted(heads):
    got = pieces.get(n, {})
    heads[n]['answer'] = ''.join(got[k] for k in sorted(got))
    print(json.dumps(heads[n], ensure_ascii=False))
PY
grep -E "GeckoConsole|TwilightMovies|AndroidRuntime|FATAL" "$OUT/$NAME-logcat.txt" | tail -n 400 > "$OUT/$NAME-log.txt"
echo "data: $(wc -l < "$OUT/$NAME-data.txt") requests written down; $(ls "$OUT" | wc -l) files"
