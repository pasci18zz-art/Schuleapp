# Schuleapp
App für die Schule

## Geld – Budget & Schulden (`geld/`)

Eine kleine Handy-App (für Android), mit der du deine Ausgaben und Schulden im Blick behältst.
Sie läuft **komplett offline**: Alle Daten bleiben nur auf deinem Handy. Es gibt keine Verbindung zur Bank,
keinen Server und kein Konto.

### Was die App kann

- **Übersicht:** wie viel Geld du diesen Monat noch hast, mit Ring, Betrag pro Tag und Konten-Balken. Mit den Pfeilen kannst du in frühere Monate blättern.
- **Konten:** eigene Konten (z. B. Essen & Snacks, Tanken) mit Monatsbudget.
- **Feste Ausgaben:** z. B. Handyvertrag oder Spotify, werden jeden Monat automatisch eingetragen.
- **Ausgabe eintragen:** mit großem Zahlenfeld. Mit „Für jemanden mitbezahlt“ landet der Betrag direkt bei den Schulden.
- **Schulden:** eine Karte pro Person mit Stand (im Plus, im Minus oder ausgeglichen)
  - **1× tippen:** eintragen, was du mitgebracht hast (Lidl-Snacks mit Preis, dazu ein eigener Betrag möglich)
  - **2× tippen:** eintragen, wie viel die Person dir gegeben hat
  - **gedrückt halten:** Übersicht der Person mit Plus/Minus und Verlauf
  - Stand per WhatsApp & Co. **teilen**
- **Rückgängig** nach jedem Eintrag
- **Rest vom Vormonat mitnehmen** (in den Einstellungen)
- **Snacks & Preise** selbst ändern, eigene **Fotos** für jeden Snack
- **Backup** als Datei speichern und wieder laden
- **Kontoauszug importieren:** CSV-Datei aus dem VR-Banking. Die Datei wird nur auf dem Handy gelesen.
- Hell- und Dunkel-Modus

### Auf dem Handy installieren

1. Einmalig auf GitHub: **Settings → Pages → Build and deployment → Source: „GitHub Actions“**.
   Danach wird die App bei jedem Push auf `main` automatisch veröffentlicht.
2. Auf dem Android-Handy in Chrome öffnen: `https://pasci18zz-art.github.io/Schuleapp/geld/`
3. Menü (⋮) → **„App installieren“** (oder „Zum Startbildschirm hinzufügen“).
4. Fertig: Die App startet jetzt wie eine normale App und funktioniert auch ohne Internet.

> Tipp: Mach ab und zu ein Backup (Einstellungen → Backup speichern). Wenn du die Browser-Daten von Chrome
> löschst oder das Handy wechselst, sind die Daten sonst weg.

### Technik

Reines HTML, CSS und JavaScript ohne Build-Schritt. Ein Service Worker (`sw.js`) speichert die App für den Offline-Betrieb,
die Daten liegen im `localStorage`.
Schrift: Plus Jakarta Sans (SIL Open Font License), lokal eingebunden.
