# Schulplaner – Plan

Android-App (Handy + Tablet, Android 12+, optimiert für Android 16/17) zum Planen von Schule:
Stundenplan, Hausaufgaben/To-dos, Klausuren und Noten (Hessen, Oberstufe, Notenpunkte).

Design-Entwürfe (klickbar): https://claude.ai/artifact/YaVbw4kmq8qm3xJXT1iN3n

## Festgelegte Entscheidungen

| Thema | Entscheidung |
|---|---|
| Name | Schulplaner |
| Schule | Gymnasium Hessen, Oberstufe (E1/E2, Q1–Q4), aktuell Q1 |
| Stunden | 45-Minuten-Blöcke, Doppelstunden als eine Stunde mit Länge 2 |
| A/B-Wochen | ja, wählbar pro Stunde (jede Woche / nur A / nur B) |
| Noten | Notenpunkte 0–15, Schnitte als Kommazahl (ohne Rundung) |
| Gewichtung | pro Fach **und** Halbjahr, Standard „Schriftlich 50 % / Mündlich 50 %“, frei änderbar, weitere Kategorien möglich; wird ins nächste Halbjahr übernommen |
| Gesamtschnitt | alle Fächer zählen gleich (LK doppelt erst in der Abi-Übersicht) |
| Design | hell/dunkel nach System, clean: ruhiger Hintergrund, weiße Kacheln mit feinem Rand, abgerundet, nur Symbole in der Navigation |
| Navigation | schmale Pille unten mit 6 Symbolen (inkl. Einstellungen), auch auf dem Tablet; Tabs per Wischen wechselbar, Wischen über die Leiste wählt den Tab unter dem Finger; Tab antippen führt immer zur Startseite des Tabs |
| Erledigte Aufgaben | bleiben ausgegraut/durchgestrichen stehen, bis ihr Fälligkeitstag vorbei ist, dann unter „Erledigt“; „Rückgängig“-Leiste |
| Kalender | im Stundenplan als Umschalter Woche/Monat; Tablet: große Monatsansicht mit Tagesleiste |
| Ferien | offizielle Hessen-Ferien + Feiertage vorinstalliert, Ferien-Countdown auf „Heute“ |
| App-Verknüpfungen | drei Kacheln auf „Heute“: Notes (Samsung Notes), Claude, Spotify – echte App-Symbole, lange drücken = andere App wählen; Handy in einer Zeile, Tablet unten links |
| Lebendigkeit | treibende Farbflächen und schwebende Punkte im Hintergrund, farbige „Jetzt“-Kachel mit Glanz, Konfetti beim Abhaken, Sonne/Mond oben, Kacheln gleiten herein |
| Erinnerung | am Vorabend (Standard 18:00) an offene HA und Klausuren für morgen |
| Nicht gewünscht | Unterkurs-Anzeige, wiederkehrende Aufgaben, Vibration, Abi-Countdown, Notizbuch-Knopf pro Fach |
| Sync | Supabase (kostenlos), offline zuerst, „neueste Änderung gewinnt“; eine Tabelle `records` (Typ, ID, JSON) mit Zeilen-Sicherheit; Fotos im Speicher-Bucket `anhaenge`; beim ersten Abgleich eines Geräts gewinnt das Konto; doppelte Halbjahre/Fächer/Kategorien werden zusammengeführt; URL + öffentlicher Schlüssel als GitHub-Secrets `SUPABASE_URL`/`SUPABASE_KEY` |
| APK | wird bei jedem Push von GitHub Actions gebaut, immer mit demselben Schlüssel signiert |

## Aufbau

- **Heute** (Startseite): aktuelle/nächste Stunde mit Restzeit, Stunden des Tages (HA-/Klausur-/Entfall-Markierung),
  offene Aufgaben, nächste Klausur mit Lernstand, Halbjahresschnitt + beste Fächer.
  Nach Schulschluss/am Wochenende automatisch der nächste Schultag. Tablet: drei Spalten.
- **Stundenplan**: Wochenraster Mo–Fr, A/B-Woche, Wochen blättern. Stunde antippen → HA eintragen,
  Entfall, anderer Raum, Vertretung (nur für diesen Tag), Notizen zum Fach. Stift → Bearbeiten-Modus.
- **Aufgaben**: Offen/Alle/Erledigt, Filter nach Art (HA/Zusatz/Sonstiges) und Fach, Gruppen nach Fälligkeit.
- **Klausuren**: Liste (anstehend/vergangen), Detail mit Countdown, Lernplan-Checkliste, Fotos/Dateien,
  Themen-Notizen, Ergebnis → wird automatisch als schriftliche Note eingetragen.
- **Noten**: Halbjahr wählen, Gesamtschnitt, alle Fächer. Fach-Detail: Schnitt + Verlauf, Gewichtung,
  Noten je Kategorie (Punkte, Begründung, Datum, Gewicht, Anhänge), „Was brauche ich?“-Rechner, Zeugnisnote.
- **Einstellungen**: Fächer, Halbjahre, Stundenzeiten, Ferien, A/B-Woche, Hell/Dunkel, Schnell-HA-Kachel.

### Schnell-HA
Kachel in den Schnelleinstellungen („HA eintragen“, wie die Taschenlampe) und App-Shortcut (lange auf das
App-Symbol drücken). Wählt automatisch das Fach der laufenden (oder gerade beendeten) Stunde und als
Fälligkeit die nächste Stunde dieses Fachs. Beides lässt sich im Formular ändern; wechselt man das Fach,
wandert das Datum mit.

## Technik

- Kotlin, Jetpack Compose, Material 3 (eigenes Farbschema), Schrift „Nunito“ (OFL, in `licenses/`)
- Room-Datenbank; jede Tabelle hat `id` (UUID), `updatedAt`, `deleted`, `dirty` → bereit für Sync
- Anhänge liegen im App-Speicher, Fotos werden verkleinert (max. 2400 px, JPEG)
- Projektstruktur: `data/` (Datenbank, Repository), `domain/` (Stundenplan- und Notenlogik), `ui/` (Screens), `quick/` (Kachel)

## Phasen

1. ✅ Grundgerüst, Datenmodell, alle Tabs, Einstellungen, Schnell-HA-Kachel, APK-Build
2. ✅ Sync mit Supabase (Login, Abgleich, Fotos)
3. ⏳ Widgets: Heute, Aufgaben (abhakbar), Klausur-Countdown, nächste Stunde
4. ✅ Erinnerung am Vorabend; ✅ Kalender, Ferien, Rückgängig, App-Verknüpfungen, neues Design
5. ⏳ Abi-Übersicht (Hessen-Regeln), Backup als Datei, Notenstatistik
6. ⏳ Feinschliff Design/Animationen

## Signierschlüssel
Damit jede neue APK als Update über die alte installiert werden kann, braucht der Build immer denselben
Schlüssel. Der liegt **nicht** im Repo, sondern als GitHub-Secrets (`SIGNING_KEYSTORE_BASE64`,
`SIGNING_PASSWORD`). Ohne Secrets signiert die CI mit einem zwischengespeicherten Debug-Schlüssel.
