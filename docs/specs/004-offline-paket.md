# Spec 004: Offline-Paket (Navigation ohne Netz)

Ziel: Eine Route (oder alle Etappen einer geplanten Reise) vorab speichern, damit Navigation und Spiel in Gebieten ohne
Netz (z. B. Lappland) laufen.

## Inhalt eines Pakets (`OfflinePack`, `:nav-core`, JSON, versioniert)
- Route: Linie (Polyline6), Länge, Dauer, alle Manöver mit Text und Sprachansagen.
- Tempolimits der ganzen Route, Länder entlang der Strecke, Spurinformationen.
- Toiletten/Rastplätze im Korridor (Overpass, 50-km-Fenster). Lücken sind kein Abbruchgrund.
- Aufgaben: der Plan mit allen aufgelösten Platzhaltern (Routenfakten, Wikipedia-Geschichten, Natur), sprachneutral als `PackQuest`.
- Kartenkacheln: im Offline-Speicher der Karte (MapLibre), nicht im Paket; das Paket merkt sich nur die Größe.

## Ablauf
- Speichern: Knopf bei der Route (aktuelle Route) oder „Alle Etappen offline speichern“ in der geplanten Reise.
  Phasen: Karte, Tempolimits, Länder, Rastplätze, Aufgaben, Speichern. Atomar (Temp-Datei, dann umbenennen).
- Öffnen: „Gespeicherte Offline-Routen“ im Trip-Bildschirm. Route, Tempolimits, Länder, Spuren und Aufgaben werden ohne
  Netz geladen; die Toiletten-Suche nutzt die gespeicherten Halte, wenn Overpass nicht erreichbar ist.

## Grenzen (bewusst)
- Keine Neuberechnung der Route ohne Netz (Valhalla läuft auf dem eigenen Server). Beim Abkommen von der Route bleibt die
  gespeicherte Route die Referenz.
- Autobahn-Betreiberdaten (Rastplätze) und Live-Aufgaben (Flugzeuge, Sprachkurse) sind nicht Teil des Pakets.
- Pakete mehrtägiger Etappen enthalten die ganze Strecke zwischen zwei festen Unterkünften.
- Keine Binärdaten im Repository; Pakete liegen nur im App-Speicher.
