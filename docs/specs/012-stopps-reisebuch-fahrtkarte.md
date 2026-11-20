# Spec 012: Zwischenstopp-Finder, Reisebuch (PDF), Fahrtkarte, Dashboard (Block 4 aus Spec 009)

## Zwischenstopp-Finder „Stopps für Kinder“ (`KidSpots`, `:nav-core`)
- Overpass-Suche bis 80 km voraus, 3 km Korridor: Spielplatz, Eisdiele, Schwimmbad/Wasserpark, Zoo/Freizeitpark/Aquarium, Burg. Geschlossene Orte (OSM `opening_hours`) fallen weg; Orte ohne Angabe bleiben mit „Öffnungszeiten unbekannt“.
- Sortierung: offen vor unbekannt, kleiner Umweg zuerst, je Art höchstens zwei. Umweg = Luftlinie hin und zurück bei ca. 40 km/h (grobe Schätzung).
- Beifahrer-Menü: Karte mit „Hinfahren“ je Treffer. Fahrer: nur Audio per Sprachbefehl („Kilo, Spielplatz/Eis/Zoo/Zwischenstopp“) – Kilo nennt den besten Vorschlag, der Beifahrer entscheidet.
- „Hinfahren“ erzeugt **eine** Mehrpunkt-Route Start → Stopp → ursprüngliches Ziel (`RouteMerge` setzt die Valhalla-Etappen zusammen; Ankunftszeit und Führung laufen durch, das Ziel bleibt). Am Stopp (≤ 60 m, < 15 km/h) startet eine Bewegungsaufgabe für die Kinder; „Weiter“ beendet nur die Pause. Hält man nicht an, verfällt der Stopp nach 400 m stillschweigend. Der Stopp kommt ins Tagebuch.
- Zu benannten Orten wird ein Wikipedia-Auszug gesucht (nur bei passendem Titel, nie ein beliebiger Treffer), am Stopp vorgelesen und mit Quelle (CC BY-SA) angezeigt.

## Reisebuch als PDF (`MemoryBook`, `MemoryBookBuilder`)
- A4-PDF auf dem Gerät: Titel mit Datum und Zahlen, je Tag Erlebnisse und Fotos (2×2), Routenskizze aus den Tagebuch-Positionen (eigene Linie, keine Kartenkacheln), Zusammenfassung. Teilen nur auf Wunsch (Cache + FileProvider), nichts wird hochgeladen.
- Die Logik (Tage, Fotoseiten, Ausdünnen, Projektion) ist getestet. Das Zeichnen läuft unter Robolectric nicht (`PdfDocument`), braucht einen Gerätetest.

## Fahrtkarte (Benachrichtigung)
- Während der Navigation zeigt die Vordergrund-Benachrichtigung einen Fortschrittsbalken (Strecke), Restdistanz und Teamziel in %.
- Ab Android 16 als **Live-Update** (`Notification.ProgressStyle`, Chip mit Restkilometern, Punkte bei 25/50/75 %, Recht `POST_PROMOTED_NOTIFICATIONS`); davor normale Benachrichtigung mit Balken. Dafür: AGP 8.11.1, compileSdk 36 (targetSdk bleibt 35). **Ob Android die Benachrichtigung wirklich als Live-Update hervorhebt, ist nur auf einem Android-16-Gerät prüfbar.**

## Beifahrer-Dashboard
- Neue Karte: Minuten bis zur nächsten Pause und Wetter am Ziel (aus der vorhandenen Wetterabfrage); das Zielwetter steht auch auf dem Kinderbildschirm.

## Offen / ungetestet
- Overpass-Treffer und Öffnungszeiten in der Praxis; Genauigkeit der Umwegschätzung; PDF auf echten Geräten (Emoji/Flaggen im PDF, Speicher bei vielen Fotos).
