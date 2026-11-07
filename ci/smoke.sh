#!/bin/bash
# Startet die App im Emulator: erst alte Version (Datenbank anlegen), dann Update, dann frische Installation.
# Abstürze landen im Job-Log.
OLD="$1"
NEW="$2"
PKG=de.schulplaner

launch() {
  adb shell am start -W -n $PKG/.MainActivity
  sleep 25
}

adb install -r "$OLD"
launch
echo "===== ALT (Build 13) ====="
adb logcat -d -b crash

adb logcat -c
adb install -r "$NEW"
launch
echo "===== UPDATE ====="
adb logcat -d -b crash
adb logcat -d | grep -E "AndroidRuntime|FATAL|Caused by|Exception" | head -80

adb uninstall $PKG
adb logcat -c
adb install "$NEW"
launch
echo "===== FRISCH ====="
adb logcat -d -b crash
adb logcat -d | grep -E "AndroidRuntime|FATAL|Caused by|Exception" | head -80
echo "===== ENDE ====="
