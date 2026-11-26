#!/bin/bash
# Bedient die App im Emulator und macht Screenshots (Startseite, Anpassen-Modus, Stimmung).
APK="$1"
OUT="$2"
PKG=de.schulplaner
mkdir -p "$OUT"
HERE="$(cd "$(dirname "$0")" && pwd)"

shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
dump() { adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb exec-out cat /sdcard/ui.xml > "$OUT/ui_$1.xml" 2>/dev/null; }
find() { python3 "$HERE/find.py" "$OUT/ui_$1.xml" "$2" "$3"; }
press() { adb shell input swipe "$1" "$2" "$1" "$2" "${3:-1000}"; }
# Finger runter, in Schritten ziehen, loslassen.
dragto() {
  local x1=$1 y1=$2 x2=$3 y2=$4 hold=${5:-0}
  local cmd="input motionevent DOWN $x1 $y1;"
  [ "$hold" != "0" ] && cmd="$cmd sleep $hold;"
  for i in 1 2 3 4 5 6 7 8 9 10; do
    cmd="$cmd input motionevent MOVE $((x1 + (x2 - x1) * i / 10)) $((y1 + (y2 - y1) * i / 10));"
  done
  cmd="$cmd input motionevent UP $x2 $y2"
  adb shell "$cmd"
}

adb install -r "$APK"
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
adb shell am start -W -n $PKG/.MainActivity
sleep 25

SIZE=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1)
W=${SIZE%x*}
H=${SIZE#*x}
DENS=$(adb shell wm density | grep -oE '[0-9]+' | tail -1)
echo "Bildschirm ${W}x${H}, Dichte ${DENS}"

shot 01_start
dump 01

# Lange auf eine echte Kachel (Tagesaufgaben) drücken -> Anpassen-Modus.
T=$(find 01 text "Tagesaufgaben")
echo "Kachel Tagesaufgaben: $T"
if [ -n "$T" ]; then
  set -- $T
  press $1 $2 1100
else
  press $((W / 2)) $((H * 70 / 100)) 1100
fi
sleep 2
shot 02_anpassen
dump 02

# Ecke ziehen (Größe), falls es einen Griff gibt.
HANDLE=$(find 02 desc "Größe ändern")
echo "Griff: $HANDLE"
if [ -n "$HANDLE" ]; then
  set -- $HANDLE
  dragto $1 $2 $(($1 - W / 4)) $(($2 + 160))
  sleep 2
  shot 03_groesser
  dump 03
  HANDLE=$(find 03 desc "Größe ändern")
  set -- $HANDLE
  [ -n "$HANDLE" ] && dragto $1 $2 $(($1 + W / 5)) $(($2 - 260))
  sleep 2
  shot 04_kleiner
  dump 04
fi

# Kachel verschieben: erste und zweite sichtbare Kachel tauschen.
A=$(find 02 desc "Kachel Tagesaufgaben")
B=$(find 02 desc "Kachel Jetzt")
echo "Kachel A: $A / B: $B"
if [ -n "$A" ] && [ -n "$B" ]; then
  set -- $B
  bx=$1; by=$2
  set -- $A
  dragto $bx $by $1 $2 1.2
  sleep 2
  shot 05_verschoben
  dump 05
fi

# Zahnrad: Einstellungen der ausgewählten Kachel.
dump 08
G=$(find 08 desc "Einstellungen")
echo "Zahnrad: $G"
if [ -n "$G" ]; then
  set -- $G
  adb shell input tap $1 $2
  sleep 2
  shot 08_einstellungen
  adb shell input keyevent KEYCODE_BACK
  sleep 2
fi

# Fertig tippen.
dump 09
F=$(find 09 text "Fertig")
if [ -n "$F" ]; then
  set -- $F
  adb shell input tap $1 $2
  sleep 2
fi
shot 06_normal
dump 06

# Sonne lang drücken -> Stimmung (mehrere Bilder der Animation).
SX=$((W - 46 * DENS / 160))
SY=$((66 * DENS / 160))
press $SX $SY 800 &
sleep 1.1
shot 07_stimmung_a
shot 07_stimmung_b
sleep 1.5
shot 07_stimmung_c
dump 07
adb logcat -d -b crash
adb logcat -d | grep -E "AndroidRuntime|FATAL|Caused by" | head -40
echo "===== FERTIG ====="
