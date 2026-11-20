# Spec 009: Backlog aus dem Agenten-Brainstorm

Quelle: fünf Fach-Agenten (Spieldesign, Fahrsicherheit/Auto, Pixel/On-Device-KI, Familien-UX, Datenschutz/Qualität). Auswahl durch den Nutzer.
Reihenfolge = Reihenfolge der Umsetzung, ein Commit pro Block.

## Block 1: Sicherheit und Datenschutz (zuerst, Review-Risiken 1, 3, 5)
- Token-Härtung: Valhalla-Token wird nicht mehr in die APK einkompiliert, sondern einmalig in den Einstellungen eingegeben (nur auf dem Gerät). R8/Minify für Release.
- Datenschutzerklärung (Text im Repo + In-App, Drittanbieter-Tabelle, Widerspruch "Position des Autos" auflösen) und Lizenz-/Attributionsseite.
- Foto-Härtung: EXIF/GPS beim Teilen entfernen, Einzel-Löschen, Export, Galerie-Kopie standardmäßig aus.
## Block 2: Spiele (Kinder)
- Kilo-Hörspiele (reines Audio), Familien-Teamziel, Kilo-Hörserie über die Reise, adaptive Schwierigkeit.
## Block 3: Fahrer und Navigation
- Maut-/Vignetten-Lotse, Funkloch-Vorwarnung, offizielle Spuranzeige im Auto, Notfall-Karte.
## Block 4: Reise, Familie, Pixel
- Zwischenstopp-Finder, Erinnerungsbuch als PDF, Live-Update-Fahrtkarte (Android 16), Beifahrer-Dashboard-Ausbau.

## Bewusst nicht jetzt
- Gemini Nano / ML Kit GenAI: Beta, proprietäre Terms, Metriken an Google, nur im Vordergrund (Konflikt mit Regeln 2/3).
- Eltern-PIN und Intent-Härtung, Einwilligungsfluss, Selbsttest-Bildschirm: später, nicht gewählt.
- Oma-Live-Link mit Server, Hintergrund-Sprachsteuerung (Play-Foreground-Service-Prüfung), E-Auto-Laden.
