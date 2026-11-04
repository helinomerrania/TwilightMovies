#!/usr/bin/env bash
# Puts what tests/try saw (screenshots, the screens' HTML or UI trees, logs) in the pre-release "try", each file
# in place of the one with the same name from the run before. Not something to install.
# Usage: tests/try/publish.sh DIR [PREFIX]
set -uo pipefail
DIR=$1
PREFIX=${2:-}
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT
for f in "$DIR"/*; do
  [ -s "$f" ] || continue   # a file, and not an empty one (GitHub takes no empty file)
  case "$f" in
    *.png | *.txt | *.log | *.tsv | *.xml | *.html | *.json) cp "$f" "$STAGE/$PREFIX$(basename "$f")" ;;
  esac
done
ls "$STAGE"
[ -n "$(ls "$STAGE")" ] || exit 0
# GitHub sometimes answers a moment's server error (HTTP 502): the upload is tried again, three times at most
for attempt in 1 2 3; do
  gh release upload try "$STAGE"/* --clobber && exit 0
  echo "the upload didn't go through (try $attempt of 3)"
  [ "$attempt" -lt 3 ] && sleep 30
done
exit 1
