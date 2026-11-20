# RouteRPG – Architektur & Roadmap (Entwurf v0.1)

Familien-Navigation mit Android Auto, bei der die aktuelle Strecke zur Schnitzeljagd wird.
Nur freie Datenquellen. Stand: 2026-11-20.

## 1. Entscheidungen aus dem Interview

| Thema | Entscheidung |
|---|---|
| Verkehr | Offene Behördendaten (DATEX II / Mobilithek u. a.) **plus** anonyme Crowd-Daten der eigenen Nutzer |
| Spielinhalt | Auto-generiert aus der Route · ortsbezogene Quizze/Geschichten · Spotting-Bingo/Alphabet-Spiele · Community-Rätsel |
| Bedienung | Kinder spielen auf Companion-Handy/Tablet, im Auto Sprachausgabe; Android-Auto-Display zeigt nur minimale Spiel-Info |
| Umfang | Gesamtplanung zuerst, dann iterativ bauen |
| Region | Europaweit (Architektur von Anfang an), Rollout Land für Land |
| Zielgruppe | Kinder 4–14, drei Altersstufen (4–6, 7–10, 11–14) |
| Sprache | Deutsch zuerst, **Internationalisierung (i18n) von Tag 1** |
| Blitzerwarnung | Nur rechtlich konform: ausschließlich fest installierte Anlagen, pro Land per Feature-Flag steuerbar (siehe 7a) |
| Monetarisierung | Zunächst kostenlos, später Pro-Features (Freemium) |
| Team | Solo-Entwicklung mit mehreren KI-Agenten |
| Testgerät | Eigenes Auto mit Android Auto (plus DHU-Emulator für CI/Szenarien) |

## 2. Plattform-Randbedingungen (wichtig)

- **Android Auto:** Navi-Apps nutzen die *Car App Library* (`androidx.car.app`, Kategorie `androidx.car.app.category.NAVIGATION`). Karte wird selbst auf eine `SurfaceContainer` gerendert (MapLibre Native). UI-Elemente sind auf `NavigationTemplate`, `PlaceListNavigationTemplate`, `RoutePreviewNavigationTemplate` usw. beschränkt.
- **Ablenkungsregeln:** Keine freien Spiele-UIs, keine Videos, begrenzte Textmenge, Aktionen während der Fahrt limitiert. Deshalb: Spiel auf dem Handy/Tablet der Kinder, im Auto nur Audio (TTS) und eine kleine Statuszeile/Benachrichtigung.
- **Play-Store-Review:** Navi-Apps in der Car-App-Kategorie werden geprüft. Das Familien-/Kinder-Spiel-Konzept sollte konform zu *Designed for Families* geplant werden (Datenschutz, keine Werbung/Tracking für Kinder).
- **Offene Daten ≠ Echtzeit-Verkehr global:** Es gibt keine freie Entsprechung zu Google/Waze-Live-Traffic. Daher Hybridansatz (Abschnitt 5).

## 3. Systemübersicht

```
┌──────────────────────── Android-Gerät (Handy) ────────────────────────┐
│  App (Kotlin, Compose)                                                │
│  ├─ :core-map      MapLibre Native, Vector Tiles, Offline-Regionen    │
│  ├─ :core-routing  Client für Valhalla/OSRM, Neuberechnung, Reroute   │
│  ├─ :core-nav      Map-Matching, Guidance, Spurhinweise, TTS          │
│  ├─ :core-traffic  Incident-Layer, ETA-Anpassung, Crowd-Upload        │
│  ├─ :game-engine   Quest-Generator, Regeln, Punkte, Persistenz        │
│  ├─ :feature-car   Car App Library (Session, Screens, Surface)        │
│  └─ :feature-kids  Companion-UI für Kinder (Handy/Tablet)             │
│        ▲ Car-Session ◄── Android Auto ──► Fahrzeugdisplay             │
└──────────────┬────────────────────────────────────────────────────────┘
               │ HTTPS
┌──────────────▼──────────────── Backend (selbst gehostet) ─────────────┐
│  API-Gateway (Ktor oder FastAPI)                                      │
│  ├─ Routing:   Valhalla (bevorzugt) / OSRM, OSM-Extrakte (Geofabrik)  │
│  ├─ Tiles:     Planetiler → PMTiles/MBTiles, Auslieferung per CDN      │
│  ├─ Traffic:   Ingest DATEX II + Crowd-Aggregator → Tile-Layer/JSON   │
│  ├─ Content:   POI-Index (Overpass/Planetiler), Wikidata/Wikipedia    │
│  ├─ Quests:    Template-Service, optional LLM-Text, Moderation        │
│  └─ Community: Rätsel, Bewertungen, Moderation (PostgreSQL + PostGIS) │
└───────────────────────────────────────────────────────────────────────┘
```

### Warum Handy-zentriert?
Android Auto spiegelt die Handy-App. Die Auto-Session läuft im selben Prozess wie die Companion-UI. Dadurch teilen sich Navigation und Spiel einen Zustand (aktuelle Route, Position, Quest-Fortschritt).

## 4. Freie Datenquellen

| Zweck | Quelle | Lizenz/Hinweis |
|---|---|---|
| Karte | OpenStreetMap, Tiles via Planetiler (OpenMapTiles-Schema) | ODbL, Attribution Pflicht |
| Routing | Valhalla oder OSRM auf OSM-Extrakten (Geofabrik) | Selbst hosten; Public-Demo-Server nicht produktiv nutzen |
| Geocoding | Photon (Komoot, open source) oder Nominatim, selbst gehostet | Nominatim-Public-API hat strikte Nutzungsregeln |
| POIs/Spielobjekte | Overpass / eigener Extrakt aus OSM | ODbL |
| Geschichten/Fakten | Wikipedia (Geosearch), Wikidata, Wikimedia Commons (Bilder) | CC BY-SA / CC0 beachten |
| Höhen/Landschaft | SRTM / Copernicus DEM | frei |
| Wetter (optional) | Open-Meteo, DWD Open Data | frei |
| Verkehrsmeldungen | Mobilithek (DE, DATEX II), weitere nationale NAPs der EU (NAP-Pflicht seit ITS-Richtlinie) | je Land prüfen |
| Baustellen/Sperrungen | dieselben NAPs, ergänzend OSM-Tags | |
| Tempolimits/Blitzer-Warnungen | OSM `maxspeed`; Blitzer-Warnung rechtlich je Land prüfen (siehe Offene Punkte) | |
| Ladesäulen/Tankstellen | OSM, Open Charge Map, Markttransparenzstelle (DE) | |
| Spielplätze, Rastplätze, Toiletten | OSM (`leisure=playground`, `amenity=toilets`, `highway=rest_area`) | wichtig für Familien |

## 5. Verkehr & Stauumfahrung

**Schicht 1 – Offizielle Meldungen:** DATEX II einlesen, auf OSM-Straßen mappen (OpenLR/AGORA-C/TMC-Location-Referenzen → Kanten). Ergebnis: Vorfälle als Layer auf der Karte und als Strafkosten im Routing.

**Schicht 2 – Crowd-Geschwindigkeiten:**
- App sendet anonymisiert (kein Nutzerbezug, zufällige Rotation von Session-IDs, Start/Ende-Zonen abschneiden, k-Anonymität ≥ 5 pro Kante) Geschwindigkeits-Samples pro OSM-Kante.
- Backend aggregiert pro Kante und Zeitfenster (5 min). Daraus: Live-Geschwindigkeitsprofil + historische Profile (Wochentag/Uhrzeit), die schon vor dem Crowd-Break-even Ankunftszeiten verbessern.
- Kaltstart-Problem: Anfangs nur Schicht 1 + historische Muster aus offenen Floating-Car-Datensätzen, soweit verfügbar.

**Umfahrung:** Valhalla unterstützt benutzerdefinierte Kantenkosten-Updates (Traffic-Tiles). Reroute-Logik auf dem Gerät: alle 60 s ETA der Alternativen holen, bei > X Minuten Vorteil Vorschlag (kind-freundlich: nicht mitten im Spiel zwingen, sondern Sprachansage + Bestätigung am Beifahrer-Handy).

## 6. Das Spielsystem („Schnitzeljagd")

### 6.1 Konzept
Beim Routenstart analysiert die App die Strecke und erzeugt ein **Abenteuer**: eine Liste von **Quests**, die an Streckenabschnitte gekoppelt sind (nach Position und Zeitfenster getriggert). Der Fahrer hört nur Ansagen, die Kinder spielen auf ihrem Gerät.

### 6.2 Quest-Typen
1. **Streckenbasiert (auto):** „Gleich seht ihr einen Fluss – wie heißt er?" (Quelle OSM/Wikidata, Antwort: Multiple Choice oder Sprache).
2. **Spotting:** Windräder, Brücken, Tunnel, Kirchtürme, Kühe – Zählen und Abhaken; Bingo-Karten pro Region/Land.
3. **Alphabet / Kennzeichen / Farben:** Klassische Autospiele mit Scoreboard.
4. **Geschichten/Quiz:** Wikipedia-basierte Kurzgeschichten zu Orten (TTS, altersgerecht gekürzt), danach Quizfragen.
5. **Etappen-Schatz:** Meilensteine („Noch 20 km bis zum Ziel der nächsten Etappe") schalten digitale Belohnungen frei (Sticker, Charakter-Fortschritt).
6. **Community-Rätsel:** Eltern/Nutzer legen Rätsel an Koordinaten oder Streckenabschnitten an; Moderation und Bewertung.
7. **Pausen-Quests:** Bei Rastplatz/Spielplatz in der Nähe: Bewegungsaufgaben und Pausen-Empfehlung.

### 6.3 Generator
- Regel-/Template-basiert (deterministisch, offline-fähig), optional ergänzt durch ein LLM nur für Textveredelung.
- Eingaben: Route, Dauer, Alter der Kinder (Profil), Schwierigkeit, Themenwahl (Tiere, Geschichte, Natur, Technik), Wetter, Tageszeit.
- Sicherheitsregel: Quests dürfen den Fahrer nie zum Hinsehen/Eingreifen auffordern; Triggerpunkte werden nicht an Kreuzungen, Autobahnausfahrten oder komplexen Fahrmanövern gesetzt.

### 6.4 Spielfortschritt
Punkte, Sticker, Sammelalbum („Tierwelt Deutschlands"), Streak pro Reise, Familien-Rangliste (lokal, keine öffentliche Kinder-Identität). Gespeichert lokal (Room), optional verschlüsselte Cloud-Sync.

### 6.5 Android-Auto-Anteil
- Karte mit Route, Manöver, ETA (regulär).
- Eigene kleine Statuszeile, z. B. „Quest 3/8 · 120 Punkte" in der Karten-Navigationsleiste.
- Sprachansagen über denselben Audiokanal wie Navigation (Audio-Ducking, Priorität für Fahrhinweise).
- Optional: Voice-Antwort der Kinder via Mikrofon im Handy.

## 7. Wichtige Fahr-Features („Mächtig")

Pflicht (MVP+):
- Turn-by-Turn mit Spurassistent, Tempolimit-Anzeige, Sprachansagen (TTS offline)
- Offline-Karten & -Routing (regionsweise Download)
- Live-Verkehr, Stauumfahrung, ETA
- Ziele: Suche, Favoriten, Zuhause/Arbeit, Zwischenstopps
- Tag/Nacht-Modus, Android-Auto-Konformität

Familien-Features:
- **Pausenplaner:** Rastplatz mit Spielplatz/WC alle X Minuten, Wickelraum, Familiencafé.
- **Kinderfreundliche Routenoption:** weniger Kurven (Reiseübelkeit), kurze Etappen, schöne Strecke (Scenic-Kostenmodell).
- **Ladesäulen/Tankstellen** mit Kinderfreundlichkeit (Spielplatz in der Nähe).
- **Ankunft für Kinder:** „Noch 17 Minuten" als Countdown/Fortschrittsbalken mit Maskottchen.
- **Reiseübelkeit-Modus:** keine Bildschirm-Quests auf kurvigen Abschnitten (Kurvigkeit aus Routengeometrie).
- **Mehrere Kinderprofile** mit Altersstufen.

Erweiterungen:
- Mehrtagesreisen / Roadtrip-Planung mit Etappenabenteuern
- Reise-Tagebuch/Fotobuch (Strecke + gesammelte Sticker, Export PDF)
- Kolonnenfahrt / Standort teilen (opt-in)
- Wetter entlang der Route, Maut-/Vignettenhinweise, Umweltzonen
- Sehenswürdigkeiten-Umwege („Umweg +6 min zur Burg")

## 8. Datenschutz & Kinder
- Datenminimierung: Nur Geschwindigkeits-Samples pro Kante, keine Trajektorien-Speicherung serverseitig.
- Kein Werbe-SDK, kein Tracking, Opt-in für Crowd-Beitrag.
- Kinderprofile bleiben lokal; Community-Inhalte nur über Eltern-Konto.
- DSGVO: Rechtsgrundlage Einwilligung, Löschkonzept, Auftragsverarbeitung für Hosting klären.

## 9. Technik-Stack (Vorschlag)

| Bereich | Wahl | Alternative |
|---|---|---|
| App | Kotlin, Jetpack Compose, Coroutines/Flow, Hilt, Room | |
| Karte | MapLibre Native Android | |
| Car | `androidx.car.app` + `androidx.car.app:app-projected` | |
| Routing | Valhalla (Traffic-Tiles, Costing-Profile) | OSRM, GraphHopper (Lizenz prüfen) |
| Geocoding | Photon | Nominatim |
| Backend | Kotlin/Ktor oder Python/FastAPI, PostgreSQL+PostGIS, Redis | |
| Tiles | Planetiler → PMTiles | |
| Hosting | Hetzner/OVH, Docker Compose → später Kubernetes | |
| CI | GitHub Actions, Gradle, Test der Car-App über *Desktop Head Unit (DHU)* | |
| Tests | Unit, Instrumented, Route-Replay (GPX), DHU-Szenarien | |

## 10. Roadmap

**Phase 0 – Fundament (2–3 Wochen)**
- Repo, Gradle-Modulstruktur, CI, Lizenzprüfung der Abhängigkeiten
- Valhalla + Tile-Server lokal/Dev-Server mit Deutschland-Extrakt
- MapLibre-Karte auf dem Handy

**Phase 1 – Basisnavigation (6–8 Wochen)**
- Standort, Map-Matching, Routing, Turn-by-Turn, TTS
- Suche/Geocoding, Favoriten
- Car App Library: Navigation-Template, Surface-Rendering, DHU-Tests

**Phase 2 – Schnitzeljagd-MVP (6 Wochen)**
- Quest-Engine mit Templates, 3 Quest-Typen (Spotting, Streckenfakten, Alphabet)
- Kinder-UI auf dem Handy, TTS-Ansagen, Punkte/Sticker
- Triggerlogik mit Sicherheitsregeln, Einstellungen/Profile

**Phase 3 – Verkehr (6–8 Wochen)**
- DATEX-II-Ingest, Incident-Layer, ETA-Anpassung
- Reroute/Umfahrungsvorschlag
- Crowd-Sampling (Opt-in) + Aggregation, historische Profile

**Phase 4 – Familienfunktionen (4–6 Wochen)**
- Pausenplaner, Reiseübelkeit-Modus, kinderfreundliche Routen
- Offline-Regionen
- Wikipedia/Wikidata-Geschichten

**Phase 5 – Community & Politur (6+ Wochen)**
- Community-Rätsel mit Moderation, Reisetagebuch
- Play-Store-/Car-App-Review, Datenschutzdokumente, Beta (Closed Testing)

**Phase 6 – Wachstum**
- Weitere Länder, Mehrtagesreisen, Kolonnenfahrt, iOS/CarPlay prüfen

## 11. Hauptrisiken

| Risiko | Gegenmaßnahme |
|---|---|
| Echtzeit-Verkehrsqualität ohne große Nutzerbasis | Offizielle Feeds + historische Profile, Crowd erst schrittweise |
| Android-Auto-Review/Ablenkungsregeln | Früh mit DHU testen, Spiel nur auf dem Handy, Audio im Auto |
| Backend-Betriebskosten (Routing, Tiles) | Offline-first, Tiles als statische Dateien via CDN, Regionen-Extrakte |
| Sicherheit/Haftung (Ablenkung) | Strikte Triggerregeln, Beifahrer-Fokus, rechtliche Prüfung |
| Content-Qualität / Kinderschutz bei Community | Moderation, Meldefunktion, Whitelist-Templates im MVP |
| Lizenz (ODbL, CC BY-SA, Attribution) | Lizenz-Audit in CI, Attribution im UI |
| Aufwand des Gesamtprojekts | Phasen mit klarem MVP-Schnitt (Phase 2) |

## 12. Zusätzliche Entscheidungen aus Runde 2

### 12.1 Internationalisierung ab Tag 1
- Alle Texte in Android-String-Ressourcen (`values/`, `values-de/` …), keine Hardcodes; Lint-Regel `HardcodedText` als Fehler.
- Quests sind **sprachneutrale Templates** (ID + Parameter) mit Textkatalogen pro Sprache (ICU MessageFormat für Plural/Genus). So kann später jede Sprache ergänzt werden, ohne den Generator anzufassen.
- Wikipedia/Wikidata liefern Inhalte pro Sprache; Fallback-Kette (Nutzersprache → Englisch → Label ohne Story).
- TTS-Stimmen: System-TTS pro Locale; Einheiten (km/mi), Datum, Zahlenformate über `java.time`/ICU.
- Übersetzung: Weblate (frei) oder Crowdin-OSS-Plan, KI-Vorübersetzung mit menschlicher Prüfung für Kindertexte.
- RTL-Unterstützung mitdenken (Layouts mit `start/end`).

### 12.2 Altersstufen 4–14
| Stufe | Alter | Spielform | Bedienung |
|---|---|---|---|
| Mini | 4–6 | Bildbasiert, Zählen, Farben, Tiere, Vorlesen per TTS | Große Buttons, kein Lesen nötig, Sprache/Icons |
| Entdecker | 7–10 | Quiz, Spotting-Bingo, Alphabet, kurze Geschichten | Lesen, einfache Texteingabe optional |
| Profi | 11–14 | Wissensfragen, Knobelaufgaben, Community-Rätsel, Rangliste, Karten-Lesen | Volle Komplexität, Mitgestalten von Rätseln |

Jede Quest trägt Metadaten (`minAge`, `maxAge`, `difficulty`); der Generator mischt Stufen, wenn Geschwister gemeinsam spielen (Team-Modus).

### 12.3 Europaweit
- Datenbasis: Europa-Extrakt (Geofabrik), aber **Rollout pro Land/Region**. Start: DE, danach AT/CH, dann Nachbarländer. Routing-Tiles für ganz Europa brauchen viel Speicher/RAM; Anfangs nur DACH hosten.
- Verkehrsdaten: pro Land ein eigener Ingest-Adapter (NAP/DATEX II), gemeinsame interne Schnittstelle `TrafficSource`. Neues Land = neuer Adapter + Mapping-Test.
- Verkehrsregeln/Einheiten/Tempolimit-Defaults pro Land in einer Konfigurationsdatei.
- Offline-Karten in Länderpaketen.

### 12.4 Blitzerwarnung (rechtlich konform)
Die Rechtslage ist je Land unterschiedlich. In Deutschland ist es nach § 23 Abs. 1c StVO dem Fahrer untersagt, ein Gerät zu betreiben oder betriebsbereit mitzuführen, das Verkehrsüberwachung anzeigt oder stört; die Schweiz verbietet Warnungen strikt, Frankreich erlaubt nur allgemeine „Gefahrenzonen" ohne genaue Position. „Nur feste Anlagen" macht es also **nicht automatisch überall legal**.

Umsetzung:
- Datenquelle: OSM (`highway=speed_camera`), nur feste Anlagen; keine Crowd-Meldungen mobiler Kontrollen.
- Pro Land eine Policy (`OFF` / `DANGER_ZONE_ONLY` / `FIXED_CAMERAS`) per Remote-Config, Standard `OFF` bzw. `DANGER_ZONE_ONLY`, bis die Rechtslage geklärt ist.
- Alternativ allgemein formulierte Hinweise („Streckenabschnitt mit Geschwindigkeitsüberwachung / Gefahrenstelle"), Tempolimit-Hinweise bleiben immer aktiv.
- Vor Aktivierung in einem Land: rechtliche Kurzprüfung dokumentieren (`docs/legal/<land>.md`). Das ist keine Rechtsberatung; für den Launch lohnt sich eine kurze Prüfung durch einen Anwalt.

### 12.5 Freemium-Vorbereitung
- Abstraktion `Entitlements` (Feature-Flags, lokal + Google Play Billing), von Anfang an im Code.
- Frei: Navigation, Basisverkehr, Basis-Schnitzeljagd, Android Auto.
- Pro (später): erweiterte Quest-Pakete (Themenwelten), Mehrtagesreisen, Reisetagebuch-Export, unbegrenzte Offline-Regionen, Familienprofile/Sync, Kolonnenfahrt.
- Keine Werbung, keine Tracking-SDKs (Familien-Compliance).

### 12.6 Solo-Entwicklung mit KI-Agenten
- **Monorepo**, klare Modulgrenzen und kleine Module, damit Agenten mit begrenztem Kontext sicher arbeiten können.
- `AGENTS.md`/`CLAUDE.md` im Repo mit Architekturregeln, Build-/Testbefehlen, Code-Konventionen, „niemals tun"-Liste (z. B. keine neuen Abhängigkeiten ohne Lizenzprüfung, keine Hardcode-Strings).
- Jede Funktion mit Tests; CI als Qualitätstor (Lint, Unit-Tests, Detekt/ktlint, Lizenz-Check). Pull-Requests pro Agent-Aufgabe, du reviewst.
- Spezifikation vor Implementierung: kurze Specs in `docs/specs/NNN-*.md` pro Feature als Eingabe für Agenten.
- Route-Replay-Tests (GPX) und DHU-Szenarien als Ersatz für aufwendige Fahrtests.
- Realistische Planung: Gesamtumfang ist groß, Fokus auf einen **MVP nach Phase 2** (spielbar im eigenen Auto) und ab dann Nutzer-Feedback.

### 12.7 Testen im eigenen Auto
- Entwicklung: Handy per USB + **Desktop Head Unit** (DHU) für schnelle Iteration.
- Im Auto: Android Auto mit Entwicklermodus aktivieren („Unbekannte Quellen"), Debug-Builds oder interner Testkanal in der Play Console.
- Testprotokoll pro Fahrt: Triggerzeitpunkte, Audio-Ducking, Ablenkung, Akku/Daten.
- Wichtig: Nur bei sicheren Bedingungen testen, eine zweite Person bedient das Handy.

## 13. Noch offen

1. **Name/Branding:** „RouteRPG" final? (Domain, Play-Store-Name, Marken-Check)
2. **Hosting-Budget:** Euro/Monat für Server? Das entscheidet, ob DACH oder ganz Europa als Backend-Start möglich ist.
3. **Rechtsform/Impressum/Datenschutz:** Privatperson oder Unternehmen? (Pflicht für Play Store und DSGVO.)
4. **iOS/CarPlay:** Später relevant? (Bei Kotlin-Stack wäre das ein eigenes Projekt.)
5. **Repo-Struktur:** Soll ich das Gradle-Projekt (Phase 0) jetzt aufsetzen?
