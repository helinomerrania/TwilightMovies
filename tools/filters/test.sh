#!/usr/bin/env bash
# Tests the ad blocker's engine (tests/java): small lists made into a filter file and read back exactly as
# the app reads its own, then asked about addresses and pages. Needs Java 17 or newer.
set -euo pipefail
cd "$(dirname "$0")/../.."
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
javac -nowarn -encoding UTF-8 -d "$WORK" \
  app/src/main/java/com/twilightcafe/movies/adblock/*.java \
  tools/filters/src/com/twilightcafe/movies/adblock/*.java \
  tests/java/com/twilightcafe/movies/adblock/*.java
java -cp "$WORK" com.twilightcafe.movies.adblock.AdBlockTest
