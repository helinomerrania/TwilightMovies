#!/usr/bin/env bash
# The Java tests, on a computer with Java 17 or newer and nothing to install: the app's plain-Java parts
# (util/, adblock/engine/, adblock/surrogates/, and every class that says "Plain Java." at its top), the ad
# blocker's filter compiler (tools/filters) and the architecture's rules (ArchitectureTest).
# Usage: tests/java/run.sh                 all of them
#        tests/java/run.sh SiteTest ...    only some
set -euo pipefail
cd "$(dirname "$0")/../.."
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
APP=app/src/main/java/com/twilightcafe/movies
plain=$( { find "$APP/util" "$APP/adblock/engine" "$APP/adblock/surrogates" -name '*.java'
           grep -rl --include='*.java' 'Plain Java\.' "$APP"; } | sort -u)
# shellcheck disable=SC2086  # one path a word
javac -nowarn -encoding UTF-8 -d "$WORK" $plain $(find tools/filters/src tests/java -name '*.java')
java -cp "$WORK" -Droot="$PWD" com.twilightcafe.movies.test.AllTests "$@"
