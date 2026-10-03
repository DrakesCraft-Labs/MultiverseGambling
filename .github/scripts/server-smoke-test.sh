#!/usr/bin/env bash
#
# Starts a real Paper server with the plugin jar and checks that it loads, enables, answers a
# command and shuts down cleanly, with no linkage error from the API of that version.
#
#   .github/scripts/server-smoke-test.sh <minecraft-version> <path-to-plugin-jar>
#
# The same jar (built once against 1.21.11) is run on every version, which is the whole point:
# a method missing or changed in a newer API only shows up when the classes are linked on that
# server, never at compile time.
#
set -euo pipefail

VERSION="$1"
PLUGIN_JAR="$(realpath "$2")"
WORK="server-$VERSION"
START_TIMEOUT="${START_TIMEOUT:-300}"
SETTLE_SECONDS="${SETTLE_SECONDS:-30}"

rm -rf "$WORK"
mkdir -p "$WORK/plugins"
cd "$WORK"

echo "::group::Download Paper $VERSION"
BUILDS="$(curl -fsSL "https://fill.papermc.io/v3/projects/paper/versions/$VERSION/builds")"
URL="$(echo "$BUILDS" | jq -r '([.[] | select(.channel == "STABLE")] + .)[0].downloads."server:default".url')"
SHA="$(echo "$BUILDS" | jq -r '([.[] | select(.channel == "STABLE")] + .)[0].downloads."server:default".checksums.sha256')"
if [ -z "$URL" ] || [ "$URL" = "null" ]; then
    echo "No Paper build found for $VERSION" >&2
    exit 1
fi
echo "Paper build: $URL"
curl -fsSL -o paper.jar "$URL"
echo "$SHA  paper.jar" | sha256sum -c -
echo "::endgroup::"

cp "$PLUGIN_JAR" plugins/
echo "eula=true" > eula.txt
cat > server.properties <<'PROPERTIES'
online-mode=false
level-type=minecraft\:flat
spawn-protection=0
max-players=2
view-distance=4
simulation-distance=4
PROPERTIES

echo "::group::Start the server"
mkfifo console
java -Xms1G -Xmx2G -jar paper.jar --nogui < console > server.log 2>&1 &
SERVER_PID=$!
# Keep the console pipe open for writing, so the server does not see an end of input.
exec 3> console

started=false
for _ in $(seq 1 "$START_TIMEOUT"); do
    if grep -q "Done (" server.log; then
        started=true
        break
    fi
    if ! kill -0 "$SERVER_PID" 2>/dev/null; then
        break
    fi
    sleep 1
done
echo "::endgroup::"

if [ "$started" != true ]; then
    echo "The server did not finish starting" >&2
    tail -n 200 server.log >&2
    kill "$SERVER_PID" 2>/dev/null || true
    exit 1
fi

# Let the tasks scheduled for after the start run (casino world, item values, poker ticker).
sleep "$SETTLE_SECONDS"
echo "mvgam info" >&3
echo "mvgam games" >&3
sleep 5
echo "stop" >&3

for _ in $(seq 1 120); do
    if ! kill -0 "$SERVER_PID" 2>/dev/null; then
        break
    fi
    sleep 1
done
if kill -0 "$SERVER_PID" 2>/dev/null; then
    echo "The server did not stop in time" >&2
    kill -9 "$SERVER_PID" 2>/dev/null || true
fi
exec 3>&-

echo "::group::Server log"
cat server.log
echo "::endgroup::"

failed=false
require() {
    if ! grep -qE "$1" server.log; then
        echo "::error::Paper $VERSION: missing in the log: $2"
        failed=true
    fi
}
forbid() {
    if grep -nE "$1" server.log; then
        echo "::error::Paper $VERSION: $2"
        failed=true
    fi
}

require "Enabling MultiverseGambling" "the plugin was not enabled"
require "Catalogue loaded:" "onEnable did not reach its end"
require "Disabling MultiverseGambling" "the plugin was not disabled on stop"
forbid "Could not load 'plugins/MultiverseGambling|Error occurred while enabling MultiverseGambling" \
       "the server refused to load or enable the plugin"
forbid "NoSuchMethodError|NoSuchFieldError|NoClassDefFoundError|AbstractMethodError|IncompatibleClassChangeError|ClassCastException" \
       "a class or member of the plugin does not link against this API"
forbid "^[[:space:]]*at com\.chagui68\." "an exception was thrown from the plugin code"

if [ "$failed" = true ]; then
    exit 1
fi
echo "Paper $VERSION: the plugin loaded, enabled, ran and stopped cleanly."
