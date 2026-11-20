# Spec 012: Zwischenstopp-Finder, Reisebuch (PDF), Fahrtkarte, Dashboard (Block 4 aus Spec 009)

## Zwischenstopp-Finder „Stopps für Kinder“ (`KidSpots`, `:nav-core`)
- Overpass-Suche bis 80 km voraus, 3 km Korridor: Spielplatz, Eisdiele, Schwimmbad/Wasserpark, Zoo/Freizeitpark/Aquarium, Burg. Geschlossene Orte (OSM `opening_hours`) fallen weg; Orte ohne Angabe bleiben mit „Öffnungszeiten unbekannt“.
- Sortierung: offen vor unbekannt, kleiner Umweg zuerst, je Art höchstens zwei. Umweg = Luftlinie hin und zurück bei ca. 40 km/h (grobe Schätzung).
- Beifahrer-Menü: Karte mit „Hinfahren“ je Treffer. Fahrer: nur Audio per Sprachbefehl („Kilo, Spielplatz/Eis/Zoo/Zwischenstopp“) – Kilo nennt den besten Vorschlag, der Beifahrer entscheidet.
- „Hinfahren“ setzt den Stopp als Ziel (wie beim Klo-Alarm), die Navigation bleibt dort offen; „Weiter zum Ziel“ berechnet die Route zum ursprünglichen Ziel neu. Der Stopp kommt ins Tagebuch.
- Kein echter Mehrpunkt-Zwischenstopp in einer Route (Valhalla-Legs werden nicht zusammengeführt).

## Reisebuch als PDF (`MemoryBook`, `MemoryBookBuilder`)
- A4-PDF auf dem Gerät: Titel mit Datum und Zahlen, je Tag Erlebnisse und Fotos (2×2), Routenskizze aus den Tagebuch-Positionen (eigene Linie, keine Kartenkacheln), Zusammenfassung. Teilen nur auf Wunsch (Cache + FileProvider), nichts wird hochgeladen.
- Die Logik (Tage, Fotoseiten, Ausdünnen, Projektion) ist getestet. Das Zeichnen läuft unter Robolectric nicht (`PdfDocument`), braucht einen Gerätetest.

## Fahrtkarte (Benachrichtigung)
- Während der Navigation zeigt die Vordergrund-Benachrichtigung einen Fortschrittsbalken (Strecke), Restdistanz und Teamziel in %.
- **Android-16-Live-Updates (`Notification.ProgressStyle`) fehlen noch:** sie brauchen compileSdk 36 und damit AGP ≥ 8.9 (aktuell 8.7.3 / compileSdk 35). Eigene Aufgabe.

## Beifahrer-Dashboard
- Neue Karte: Minuten bis zur nächsten Pause und Wetter am Ziel (aus der vorhandenen Wetterabfrage).

## Offen / ungetestet
- Overpass-Treffer und Öffnungszeiten in der Praxis; Genauigkeit der Umwegschätzung; PDF auf echten Geräten (Emoji/Flaggen im PDF, Speicher bei vielen Fotos).
