#!/bin/bash
# Shuts down every running Android emulator.
#
# The Pixel is often connected over USB at the same time, so this matches only
# emulator- serials and leaves physical devices running.

set -euo pipefail

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

if ! command -v adb &>/dev/null; then
    echo "adb not found; set ANDROID_HOME to your SDK." >&2
    exit 1
fi

# `adb devices` prints a header and then one "<serial>\t<state>" line per
# device. Take every emulator whatever its state: a wedged one still needs
# killing.
serials=$(adb devices | awk '/^emulator-/ { print $1 }')

if [ -z "$serials" ]; then
    echo "No emulator is running."
    exit 0
fi

status=0
for serial in $serials; do
    echo "Stopping $serial"
    # One unresponsive emulator must not leave the rest running.
    adb -s "$serial" emu kill || {
        echo "   could not stop $serial" >&2
        status=1
    }
done
exit "$status"
