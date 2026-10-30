#!/usr/bin/env bash
# Makes the ad blocker's filter file: downloads the lists in tools/filters/lists.txt, compiles them
# (adblock/compiler/FilterCompiler) into filters.bin and filters.json, and checks the result (tools/filters/checks.txt).
# Usage: tools/filters/build.sh OUTPUT_FOLDER     (needs Java 17 or newer, and curl)
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT=${1:?the folder to put filters.bin in}
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/classes" "$WORK/lists" "$OUT"

# the app's own engine (adblock/engine: how the file is written and read) and the compiler (tools/filters/src)
javac -nowarn -encoding UTF-8 -d "$WORK/classes" \
  $(find app/src/main/java/com/twilightcafe/movies/adblock/engine tools/filters/src -name '*.java')

args=()
missing=()
while read -r name format urls; do
  [[ -z "${name:-}" || "$name" == \#* ]] && continue
  file="$WORK/lists/$name.txt"
  got=""
  for url in $urls; do
    if [[ "$url" == file:* ]]; then
      cp "${url#file:}" "$file"
      got=$url
      break
    fi
    if curl -fsSL --retry 3 --retry-delay 5 --connect-timeout 20 --max-time 300 -A "TwilightMovies-filters" -o "$file" "$url" \
       && [[ $(wc -c < "$file") -gt 10000 ]]; then   # anything smaller is an error page, not a list
      got=$url
      break
    fi
    echo "  $name: $url didn't answer, trying the next copy"
  done
  if [[ -z "$got" ]]; then
    missing+=("$name")
    continue
  fi
  echo "  $name: $(wc -l < "$file") lines from $got"
  args+=("$name=$format:$file")
done < tools/filters/lists.txt

if [[ ${#missing[@]} -gt 0 ]]; then
  echo "Couldn't download: ${missing[*]}"
  exit 1
fi

java -Xmx2g -cp "$WORK/classes" com.twilightcafe.movies.adblock.compiler.FilterCompiler \
  --out "$OUT" --checks tools/filters/checks.txt "${args[@]}"
