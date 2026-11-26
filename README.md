# Schulplaner

Android-App für Stundenplan, Hausaufgaben, Klausuren und Noten (Oberstufe Hessen, Notenpunkte).

## APK installieren
1. Auf GitHub unter **Releases → „Schulplaner – neueste Version“** die `.apk` herunterladen
   (oder unter **Actions → letzter Lauf → Artifacts**).
2. Auf dem Handy öffnen und „Installation aus unbekannten Quellen“ für den Browser/Dateimanager erlauben.
3. Updates einfach drüber installieren – die Daten bleiben erhalten.

## Selbst bauen
```
./gradlew assembleRelease
```
Die APK liegt dann unter `app/build/outputs/apk/release/`.

Mehr zum Aufbau und zu den nächsten Schritten: [PLAN.md](PLAN.md)
