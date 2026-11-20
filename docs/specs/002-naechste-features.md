# Spec 002: Nächste Feature-Runde (Ergebnis Recherche + Interview)

Entscheidungen des Nutzers:
- Alle vier Richtungen, in dieser Reihenfolge: Mehrtagesreise, Offline-Paket, Hörgeschichten & Reisetagebuch, freihändige Sprachbedienung.
- E-Auto-Ladeplanung: später, nur als Idee (Roadmap).
- Blitzerwarnung: Länder-Policy mit Erlaubnisliste (Standard AUS; nur Länder mit belegter Rechtslage).
- Fokus: neue Features bauen, Gerätetests laufen nebenher.

## 1. Mehrtagesreise
- Tagesetappen aus Fahrzeit-Budget (Einstellung), Übernachtungsstopps in `nav-core` (reine Logik, Tests).
- Favoriten (Hotel/Camping/Zuhause) lokal gespeichert, kein Cloud-Dienst.
- Etappen-Übersicht im Eltern-Bildschirm; Quests pro Tag vorladen.

## 2. Offline-Paket
- Route, Anweisungen, vorgeplante Quests, Toiletten/Rastplätze entlang der Route als lokales Paket (JSON, keine Binärdaten im Repo).
- Karten-Kacheln: Nutzung nur, wenn Lizenz/Tile-Server es erlauben; sonst Hinweis in den Einstellungen.

## 3. Hörgeschichten & Reisetagebuch
- Geschichten pro Ort aus freien Quellen (Wikipedia/Lizenz CC BY-SA mit Attribution), nur Audio für den Fahrer.
- Tagebuch: erledigte Quests, Stempel, Strecke, Länder; lokal, kein Tracking.

## 4. Freihändige Sprachbedienung
- Gemini-Intents für Navigation starten/suchen/eigene Aktionen (Pause, Klo-Alarm), Fahrer nur Audio.
- Spracherkennung offline bevorzugt; keine proprietären Analytics.

## 5. Blitzer-Policy
- `CameraPolicy` je Länderkürzel: ALLOWED / TEMPO_ONLY / OFF. Standard OFF.
- Schweiz: OFF (belegt verboten). Deutschland: widersprüchliche Quellen -> OFF.
- Erlaubnisliste wird erst nach belegter Rechtsprüfung je Land erweitert; Tempolimit-Anzeige überall zulässig.
- Datenquelle muss frei sein (OSM `highway=speed_camera`, ODbL).

## Idee (später)
- E-Auto: Ladestopps nach Ausstattung (Toilette, Spielplatz) wählen und mit Kinder-Pause verbinden (Open Charge Map, Key nötig).
