#!/usr/bin/env bash
# What the tries on the emulator do with it (emulator.sh and twilight.sh source this): a screenshot with the
# screen's UI tree (shot), the remote's keys (key), a tap on what the screen says (tap), and a button of the app's
# menu (choose). They write to $OUT, each file named $NAME-<step number>-<step>.
n=${n:-0}

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

# In the app's menu: down, until the button whose words match $1 has the remote; then OK on it.
choose() {
  local i
  for i in $(seq 1 14); do
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
    if adb shell cat /sdcard/ui.xml | python3 -c '
import re, sys
xml = sys.stdin.read()
for node in re.findall(r"<node [^>]*>", xml):
    if "focused=\"true\"" in node:
        words = " ".join(w for w in re.findall(r"(?:text|content-desc)=\"([^\"]*)\"", node) if w)
        sys.exit(0 if re.search(sys.argv[1], words, re.I) else 1)
sys.exit(1)' "$1"; then
      echo "choose $1"
      key KEYCODE_DPAD_CENTER
      return 0
    fi
    key KEYCODE_DPAD_DOWN
  done
  echo "nothing in the menu matches $1"
  return 1
}
