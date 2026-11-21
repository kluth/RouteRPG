# 015 – Kennzeichen, Tiere, Himmel, Etappen, Selbsttest

## Umgesetzt
- **Kennzeichen-Bingo + Länder-Sammlung** (`game/PlateBingo.kt`, UI `ui/kids/KidsExtras.kt`): 3x3-Karte mit Seed, 42 Länder, Sammlung bleibt über Karten hinweg (lokal in SharedPreferences), Landesnamen über `Locale`, Haken statt nur Farbe.
- **Tierwelt-Quiz** (`game/WildlifeQuiz.kt`, `nav/GbifParser.kt`, `fetchWildlife` in `Net.kt`): GBIF nur mit CC0/CC BY, Position auf 0,1 Grad gerundet, Offline-Rückfall mit 20 Arten. Mini sieht nie „falsch“. Quelle wird angezeigt.
- **Himmel** (`nav/Sky.kt`): Mond, Venus, Mars, Jupiter, Saturn, lokal berechnet (Näherung ~0,3–1°), Liste „heute Abend“. Die ISS fehlt (SGP4 zu aufwändig).
- **Kindernamen** (`AppSettings.playerNames`): nur lokal, bis 4 Namen à 16 Zeichen, in Kinderansicht, Wertung und Ansagen.
- **Tagesplan Mehrtagesreise** (`nav/TripOverview.kt`, Karte im `TripScreen`): Tage, Strecke, Zeit, Aufgaben-Vorschlag, Erinnerung am Vorabend. Übernachtungssuche (`LodgingQuery`) ist als Logik vorhanden, aber noch nicht in der Oberfläche.
- **DATEX-II-Parser** (`nav/TrafficDatex.kt`): Baustellen/Staus entlang der Route, nur als Logik mit Test-Fixture.
- **Selbsttest** (Einstellungen > Selbsttest): Server-Token, Standort, Mikrofon, Spracherkennung, Bluetooth, Benachrichtigungen, Offline-Paket, Kamera; Symbol + Wort statt nur Farbe.

## Nicht umgesetzt
- **Baustellen/Stau live:** Der Parser ist gegen ein selbst gebautes Beispiel getestet, nicht gegen echte Mobilithek-Daten. Zugang, Zertifikat und Lizenz je Datensatz sind offen, daher keine Anbindung in der App.
- **Spiele im Auto-Display im Stand:** nicht gebaut (Fahrsicherheit, Regel 4; braucht Test im DHU/Auto).
- **Grafische Sticker:** nicht gebaut (braucht Gestaltung).
- ISS-Überflüge, Übernachtungsvorschläge in der Oberfläche.

## Ungetestet
Echte Geräte, GBIF-Netzabruf, TalkBack, Schriftgröße 2,0.
