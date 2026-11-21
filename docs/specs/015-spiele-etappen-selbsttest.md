# 015 – Kennzeichen, Tiere, Himmel, Etappen, Selbsttest

## Umgesetzt
- **Kennzeichen-Bingo + Länder-Sammlung** (`game/PlateBingo.kt`, UI `ui/kids/KidsExtras.kt`): 3x3-Karte mit Seed, 42 Länder, Sammlung bleibt über Karten hinweg (lokal in SharedPreferences), Landesnamen über `Locale`, Haken statt nur Farbe.
- **Tierwelt-Quiz** (`game/WildlifeQuiz.kt`, `nav/GbifParser.kt`, `fetchWildlife` in `Net.kt`): GBIF nur mit CC0/CC BY, Position auf 0,1 Grad gerundet, Offline-Rückfall mit 20 Arten. Mini sieht nie „falsch“. Quelle wird angezeigt.
- **Himmel** (`nav/Sky.kt`): Mond, Venus, Mars, Jupiter, Saturn, lokal berechnet (Näherung ~0,3–1°), Liste „heute Abend“. Die ISS fehlt (SGP4 zu aufwändig).
- **Kindernamen** (`AppSettings.playerNames`): nur lokal, bis 4 Namen à 16 Zeichen, in Kinderansicht, Wertung und Ansagen.
- **Tagesplan Mehrtagesreise** (`nav/TripOverview.kt`, Karte im `TripScreen`): Tage, Strecke, Zeit, Aufgaben-Vorschlag, Erinnerung am Vorabend. Pro Tag mit berechnetem Tagesende gibt es „Unterkunft suchen“ (Overpass, bestehender Dialog); die gewählte Unterkunft wird als gebuchte Unterkunft gespeichert und ist dann festes Tagesende.
- **DATEX-II-Parser** (`nav/TrafficDatex.kt`): Baustellen/Staus entlang der Route, nur als Logik mit Test-Fixture.
- **Selbsttest** (Einstellungen > Selbsttest): Server-Token, Standort, Mikrofon, Spracherkennung, Bluetooth, Benachrichtigungen, Offline-Paket, Kamera; Symbol + Wort statt nur Farbe.

- **Grafische Sticker:** 7 neue Vektor-Sticker (Wolf, Pinguin, Hase, Schneeflocke, Nordlicht, Schloss, Schiff) im Stil der vorhandenen; jetzt hat jeder der 16 Sticker ein Bild.
- **Quiz im Auto-Display (nur im Stand):** `car/ParkedQuizScreen.kt`, erlaubtes ListTemplate, Knopf in der Kartenleiste. Nur wenn in den Einstellungen eingeschaltet (Standard aus), Navigation läuft und gemessene Geschwindigkeit < 3 km/h; ohne Messwert gesperrt. Schließt sich von selbst, sobald das Auto fährt. Kein `distractionOptimized`, Fragen offline.

## Nicht umgesetzt
- **Baustellen/Stau live:** Der Parser ist gegen ein selbst gebautes Beispiel getestet, nicht gegen echte Mobilithek-Daten. Zugang, Zertifikat und Lizenz je Datensatz sind offen, daher keine Anbindung in der App.
- ISS-Überflüge.

## Ungetestet
Echte Geräte, Android Auto/DHU (Quiz-Knopf, Template-Grenzen, Schließen beim Losfahren), GBIF-Netzabruf, TalkBack, Schriftgröße 2,0.
