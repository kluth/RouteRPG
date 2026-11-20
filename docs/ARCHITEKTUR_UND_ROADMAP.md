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

## 14. Entscheidungen Runde 3 (Phase-0-Start)

- **Arbeitsname:** **KiloQuest** (Kilometer + Quest; international aussprechbar). Paket `app.kiloquest`. Der GitHub-Repo-Name `RouteRPG` bleibt vorerst.
- **Nutzung:** privat, nur Android, kein iOS.
- **Budget ≈ 0 €:** Backend zunächst minimal. Karte über OpenFreeMap (frei, ohne Key) für Entwicklung und privaten Gebrauch, später Offline-PMTiles. Routing per Valhalla auf eigenem PC oder Always-Free-VM; Verkehrs-Ingest erst in Phase 3. Keine Pflichtkosten vor einer Veröffentlichung.
- **Gebaut in Phase 0:** Gradle-Monorepo (`:app`, `:game-engine`), MapLibre-Karte, Quest-Generator mit Tests, i18n (EN Standard, DE), CI, `AGENTS.md`, Valhalla-Compose.

## 15. Stand Phase 1 (in Arbeit)

Fertig und getestet (Unit-Tests grün): `:nav-core` mit Geometrie/Projektion, Polyline-Decoder, Valhalla-Anfrage/-Parser, Guidance-Engine (Fortschritt, Off-Route, Ankunft), Ansage-Planer.
Gebaut, noch nicht auf dem Gerät erprobt: Zielsuche (Photon), Routenabfrage, Routenlinie auf der Karte, Sprachansagen (TTS), Neuberechnung bei Abweichung.
Android Auto: `KiloQuestCarService` (Kategorie NAVIGATION), `NavScreen` mit `NavigationTemplate` (Abbiegeinfo, Stopp-Button) und Canvas-Karte (Route + Position) auf der Auto-Surface. Handy-UI und Auto teilen den Zustand über `NavController` (Application-Prozess). Gebaut/lint-geprüft, **noch nicht im Auto/DHU getestet**; Host-Validator erlaubt aktuell alle Hosts (nur privat/Sideload).
Offen in Phase 1: Test mit DHU und im Auto, MapLibre-Karte statt Canvas im Auto, Abbiegepfeile, Tempolimit/Spurhinweise, Favoriten, Zielsuche aus dem Auto.

## 16. Stand Phase 2 (Schnitzeljagd-MVP)

Gebaut (Build, Lint und 15 Unit-Tests grün; **nicht auf dem Gerät erprobt**):
- `:game-engine`: Quest-Katalog (20 Aufgaben: Spotting, Zählen, Alphabet, Quiz mit Multiple Choice, Bewegungspausen), Generator mit Altersstufen und **Sicherheitsregel** (keine Quest im Umkreis von 300 m um eine Abbiegung), `AdventureSession` (aktive Quest, Punkte, Sticker alle 3 Aufgaben, unbeantwortete Quest verfällt).
- i18n: alle Quest-Texte EN + DE als Ressourcen; Test prüft, dass jede Katalog-Aufgabe in jeder Sprachdatei existiert und beide Dateien dieselben Schlüssel haben.
- App: Kinderansicht („Aufgaben"-Button) mit Altersauswahl, Punkten, Stickern und großen Buttons; neue Aufgabe wird zusätzlich per Sprache angesagt (Fahrer bekommt nur Audio). Altersstufe wird gespeichert.

Nachgezogen (Phase 2, zweiter Schritt; Build/Lint/Tests grün, nicht auf dem Gerät erprobt):
- **Streckenfakten:** Platzhalter-Quests werden beim Auslösen per Overpass (OSM) mit der Umgebung gefüllt (Fluss, See, Burg, Gipfel, Brücke, mit Ortsname); bei Fehler oder keinem Treffer gibt es eine Rückfall-Aufgabe. Wikipedia-Geschichten folgen in Phase 4.
- **Sticker-Album:** 16 Sticker (Emoji, sprachneutral), werden zufällig aus den fehlenden vergeben, gespeichert in den App-Einstellungen. Gesamtpunkte über alle Fahrten werden gespeichert.
- **Team-Modus:** 1–4 Kinder, reihum dran, Punkte pro Kind, Sticker für das ganze Team, Ansage nennt, wer dran ist.
- **Ansagen-Priorität:** Navigationsansagen unterbrechen Quest-Ansagen; Quests werden frühestens 8 s nach einer Navigationsansage gesprochen und verfallen nach 60 s Wartezeit (`SpeechArbiter`).

Offen: Wikipedia-Geschichten zu Orten, eigene Namen für Kinder, Sticker-Motive grafisch statt Emoji, Community-Rätsel.

## 17. Stand Phase 3 (Verkehr, Deutschland)

Gebaut (Build/Lint/Tests grün; **noch nicht im Auto/auf dem Gerät erprobt**):
- **Quelle DE:** Autobahn GmbH Verkehrs-API (`verkehr.autobahn.de`, frei, ohne Schlüssel). Liefert Warnungen (Stau, Unfall, Gefahr) mit Reisezeitverlust in Minuten und exaktem Streckenverlauf sowie Sperrungen. Parser gegen echte Antworten getestet.
- **Abgleich mit der Route:** Meldung zählt nur, wenn sie ≤ 40 m von der Route liegt **und** in Fahrtrichtung zeigt (Gegenfahrbahn wird ignoriert). Die Autobahnen werden aus den Straßennamen der Route erkannt (max. 10), Abruf alle 5 Minuten während der Fahrt.
- **Ansage und Anzeige:** Relevante Meldungen (gesperrt oder ≥ 10 Min. Verzögerung) innerhalb von 100 km werden einmal angesagt, Banner in der App, rote Punkte auf der Karte.
- **Umfahrung:** Bei relevanter Meldung berechnet die App über Valhalla (`exclude_polygons` um die Meldung) eine Alternativroute und schlägt sie vor, wenn sie ≥ 5 Min. spart oder die Strecke gesperrt ist. Annehmen/Ablehnen auf dem Handy-Display und im Auto (Aktionsleiste). Nie automatisch.
- **Quests nach Routenwechsel:** Quest-Positionen zählen jetzt über die Gesamtfahrt (`questOffset`), damit Aufgaben nach einer Umfahrung nicht verrutschen.

Grenzen/Offen:
- **Nur Deutschland.** Dänemark und Schweden fehlen: Schweden (Trafikverket Open API) braucht einen kostenlosen API-Schlüssel, für Dänemark ist noch keine frei nutzbare Quelle bestätigt.
- Planungsdaten (Baustellen ohne Sperrung) werden bewusst nicht als Problem gewertet, um Fehlalarme zu vermeiden.
- Keine Crowd-Daten und keine Live-Geschwindigkeiten; die ETA ist nur um die gemeldete Verzögerung angepasst.
- Meldungstexte kommen im Original (Deutsch) aus der Quelle; Typ und Ansagen sind übersetzt.

## 18. Stand Tempolimit & Spurhinweise

Gebaut (Build/Lint/Tests grün; Datenformate gegen die echte Valhalla-API geprüft, **App selbst noch nicht auf dem Gerät/im Auto erprobt**):
- **Tempolimit:** Aus Valhalla `/trace_attributes` (`edge_walk` über die Route, Einzelkanten mit `speed_limit` aus OSM `maxspeed`). Es wird fensterweise nachgeladen (25 km voraus, neu ab 5 km vor Fensterende), bei Fehler nach 30 s erneut. Anzeige als Tempolimit-Schild plus aktuelle Geschwindigkeit; Überschreitung (mehr als 3 km/h drüber) färbt das Schild rot. „Kein Limit" (255) zeigt ∞. Unbekanntes Limit: kein Schild. Keine Blitzerwarnung, nur Limit-Anzeige.
- **Spurhinweise:** Zweite Routenanfrage im OSRM-Format (`format: osrm`) liefert pro Kreuzung die Spuren (gültig/aktiv); sie werden anhand der Manöverposition der nativen Route zugeordnet. Anzeige als Pfeilleiste ab 900 m vor dem Manöver, gesprochener Hinweis einmal ab 700 m („Haltet euch links/rechts/Nehmt die mittleren Spuren"), nur wenn die gültigen Spuren eindeutig links/mittig/rechts liegen.
- **Auto:** Tempolimit-Schild und Spurpfeile werden auf die Auto-Karte gezeichnet (eigener Canvas). Die offiziellen Spur-Elemente der Car App Library brauchen ein Spurbild (Icon) und sind noch nicht umgesetzt.

Grenzen: OSM-Daten sind lückenhaft (nicht überall Limit/Spuren hinterlegt, variable Anzeigen fehlen), wechselnde Limits (Zeit/Wetter) sind nicht abgedeckt. Die Spuren-Zuordnung setzt voraus, dass beide Anfragen dieselbe Route liefern; bei Abweichung bleiben Spuren leer.

## 19. Stand: Zielsuche im Auto & Wikipedia-Geschichten

Gebaut (Build/Lint/Tests grün; nicht im Auto/auf dem Gerät erprobt):
- **Zielsuche im Auto:** `SearchTemplate` (Tastatur/Sprache laut Host-Regeln), Ergebnisse als Liste, ohne Eingabe die letzten 5 Ziele (auch in der Handy-Suche). In der Aktionsleiste: „Suchen" und, sobald eine Route berechnet ist, „Start". Fehler (z. B. keine GPS-Position) erscheinen als Auto-Toast.
- **Wikipedia-Geschichten:** Platzhalter-Quests werden unterwegs per Wikipedia-Geosearch gefüllt: Ort in 8 km Umkreis, Intro-Auszug in der Gerätesprache (Rückfall Englisch), Länge nach Altersstufe (1/2/3 Sätze, max. 170/280/450 Zeichen), Klammern entfernt, Orte bevorzugt, die Kinder sehen können (Burg, Berg, See, Brücke …, mehrsprachige Stichwörter). Quelle „Wikipedia (CC BY-SA)" wird in der Kinderansicht angezeigt. Getestet gegen eine echte Wikipedia-Antwort; Schwedisch (Kiruna) funktioniert.

## 20. Verkehrsdaten Dänemark und Schweden (Recherche, Stand 20.11.2026)

- **Dänemark:** Keine frei nutzbare, dokumentierte Schnittstelle ohne Anmeldung gefunden. Der offizielle Weg ist der nationale Zugangspunkt von Vejdirektoratet (`nap.vd.dk`, „The data exchanger", DATEX II); Zugang und Lizenz dort sind nicht ohne Anmeldung einsehbar. Die Karte `trafikkort.vejdirektoratet.dk` nutzt ein internes Backend (`trafikkort-prod.appspot.com`), das **nicht** als offene API veröffentlicht ist und deshalb nicht verwendet wird (keine dokumentierten Nutzungsbedingungen, kann sich jederzeit ändern). Nächster Schritt: Konto bei nap.vd.dk anlegen, Beispielantwort sichern, dann Parser bauen.
- **Schweden:** Trafikverket Open API / DATEX II, kostenloser Schlüssel nötig (CC0-Lizenz laut Trafikverket). Ohne Schlüssel kein Test der Antwortstruktur möglich.

## 21. Erweiterungen: Interview Runde 1 (20.11.2026)

Gewünschte Schwerpunkte (Reihenfolge wird in Runde 2 geklärt):
1. **Offline in Funklöchern** (Lappland): Karte und Routing ohne Netz.
2. **Reiseplanung & Pausen:** Pausenplaner, Mehrtagesreise mit Etappen, Wetter & Sonnenzeiten (kein E-Auto-Laden).
3. **Mehr Spielspaß:** Natur-Quests (GBIF/iNaturalist), Länder-Mini-Sprachkurs, Himmel & Flugzeuge, eigene Rätsel von Eltern & Sprachantworten.
4. **Reiseübelkeit-Modus:** keine Bildschirm-Aufgaben auf kurvigen Abschnitten, stattdessen Hör-/Blickaufgaben, Pausenhinweis.

Recherche-Fakten (geprüft am 20.11.2026): Autobahn-API liefert zusätzlich Ladestationen, Lkw-Parkplätze, Baustellen, Webcams (frei); GBIF und iNaturalist frei nutzbar; Open-Meteo frei für nicht kommerzielle Nutzung; Open Charge Map braucht Schlüssel; OpenSky (Flugzeuge) nicht geprüft. Android Auto: Spiele nur im Stand erlaubt, daher Spiel auf dem Handy, im Auto nur Audio.

## 22. Erweiterungen: Entscheidungen Runde 2 und Reihenfolge

- **Offline:** „Reise-Paket": Vor der Abfahrt wird ein Korridor um die Route geladen (Karte, Route, Tempolimits, Spuren, Geschichten, Wetter). Kein Routing auf dem Gerät; Neuberechnung bei Abweichung nur mit Netz.
- **Pausenplaner:** automatisch nach Altersstufe (MINI ca. 60 Min., EXPLORER ca. 75, PRO ca. 100 Min. Fahrzeit), Vorschlag für Rastplatz/Spielplatz/WC voraus, Annahme per Knopfdruck, Bewegungsaufgabe bei Ankunft.
- **Mehrtagesreise:** Etappenziele setzt der Nutzer selbst; die App plant Strecke, Pausen und Abenteuer je Etappe.
- **Reihenfolge („klein & schnell zuerst"):** 1. Reiseübelkeit-Modus, 2. Pausenplaner, 3. Wetter & Sonnenzeiten, 4. Natur-Quests, 5. Mehrtagesreise, 6. Länder-Mini-Sprachkurs, 7. Himmel & Flugzeuge, 8. Eltern-Rätsel & Sprachantworten, 9. Reise-Paket (Offline).

## 23. Stand Erweiterungen 1 und 2: Reiseübelkeit-Modus und Pausenplaner

Gebaut (Build/Lint/Tests grün; nicht auf dem Gerät erprobt):
- **Reiseübelkeit-Modus** (Schalter in der Kinderansicht, Standard an): Die Route wird auf Kurvigkeit analysiert (Richtungsänderung pro km, einzelne Kreuzungsabbiegungen zählen nicht). Auf kurvigen Abschnitten erscheint in der Kinderansicht „Blick nach draußen", neue Bildschirm-Aufgaben werden bis zur nächsten geraden Strecke zurückgestellt, und es gibt höchstens alle 3 Minuten einen gesprochenen Hinweis (3 Varianten).
- **Pausenplaner:** Zählt Fahrzeit seit der letzten Pause (≥ 5 Min. Stillstand setzt zurück). Intervall nach Altersstufe: 60/75/100 Min. Dann sucht die App per Overpass 4–45 km voraus Rastplätze, Raststätten, Spielplätze, WC (≤ 700 m von der Route) und wählt den besten (Spielplatz/WC in der Nähe zählen, Umweg kostet). Vorschlag per Ansage und Karte im Handy-Display und in der Auto-Aktionsleiste: „Pause einlegen" / „Später" (Erinnerung nach 10 Min.). Bei Ankunft (≤ 400 m, langsam) erscheint eine Bewegungsaufgabe; Zeit-Zähler wird zurückgesetzt.
- Grenzen: Der Vorschlag führt nicht automatisch dorthin (liegt aber höchstens 700 m neben der Route); OSM-Daten für Rastplätze sind lückenhaft, besonders in Schweden/Lappland. Die Schwellen (150°/km, Intervalle) sind Schätzwerte und gehören auf der Fahrt kalibriert.

## 24. Stand Erweiterung 3: Wetter & Sonnenzeiten

Gebaut (Build/Lint/Tests grün; **Open-Meteo-Antwortformat nur nach Dokumentation und mit eigenen Testdaten geprüft**, die Live-Abfrage war von der Entwicklungsumgebung aus wegen eines Tageslimits für deren gemeinsame IP nicht möglich; auf dem Handy bitte beim ersten Test prüfen):
- Wetter an bis zu 12 Stützpunkten der Route (etwa alle 75 km, inkl. Ziel) zur geschätzten Ankunftszeit dort, per Open-Meteo (frei, nicht kommerziell, ohne Schlüssel). Aktualisierung alle 30 Min. während der Fahrt.
- Anzeige: Wetter am Ziel (Symbol, Text, Temperatur), bis zu zwei Gefahren voraus mit Entfernung (Starkregen, Schneefall, Glätte, Gewitter, Nebel, Böen ≥ 70 km/h, Frost ≤ −15 °C), Hinweis „Ankunft nach Einbruch der Dunkelheit"; bei Polarnacht (keine Sonnenzeiten) bleibt dieser Hinweis weg.
- Ansage zum Fahrtstart (Zielwetter) und je Aktualisierung höchstens eine neue Gefahr.
- Grenze: Vorhersage-Genauigkeit der Open-Meteo-Modelle; die Ankunftszeit ist linear aus der Routendauer geschätzt (ohne Verkehr/Pausen).

## 25. Stand Erweiterung 4: Natur-Quests

Gebaut (Build/Lint/Tests grün, gegen echte iNaturalist-Antwort für Kiruna getestet: Ren, Elch, Schneehase; Duisburg: Rotkehlchen, Stockente):
- Platzhalter-Aufgaben werden unterwegs per iNaturalist `species_counts` (25 km Umkreis, nur „research grade", nur Vögel und Säugetiere, Namen in Gerätesprache, Rückfall Englisch) gefüllt: „Hier leben: Elch. Vielleicht entdeckt ihr eins!". Auswahl gewichtet nach Häufigkeit, Säugetiere doppelt. Ohne Netz/Treffer gibt es eine Rückfall-Aufgabe. Quelle „Daten: iNaturalist-Community" in der Kinderansicht.
- Grenze: Es sind Beobachtungen der Umgebung, keine Garantie, dass man das Tier vom Auto aus sieht; Kinder-Tierbilder/Album mit Arten folgen ggf. später.

## 26. Stand Erweiterung 5: Mehrtagesreise

Gebaut (Build/Lint/Tests grün, Etappen-Parser gegen echte Valhalla-Antwort getestet; nicht auf dem Gerät erprobt):
- **Reiseplaner** („Reise"-Button neben der Suche): Etappenziele selbst hinzufügen/entfernen. Eine Valhalla-Anfrage liefert Länge und Dauer je Etappe und die Summe; Etappen über 8 Stunden werden mit „Langer Tag – plant eine zusätzliche Pause ein" markiert.
- **Jede Etappe = eigene Route mit eigenem Abenteuer:** „Etappe n starten" berechnet die Route von der aktuellen Position zum Etappenziel und startet die Navigation. Am Etappenziel werden die Punkte des Tages gespeichert, die nächste Etappe wird freigegeben und angesagt („Etappe 2 geschafft! … Punkte heute"). Die Reise bleibt über App-Neustarts und mehrere Tage erhalten (lokal gespeichert).
- Grenzen: Übernachtungsvorschläge (Camping/Hotel) sind noch nicht eingebaut; die Etappenzeiten sind reine Fahrzeit ohne Pausen und Verkehr; Reihenfolge der Ziele ist vom Nutzer vorgegeben (keine Optimierung).

## 27. Stand Erweiterung 6: Länder-Mini-Sprachkurs

Gebaut (Build/Lint/Tests grün; nicht auf dem Gerät erprobt, besonders die Sprachausgabe in der Landessprache):
- **Kurse** für Schweden, Dänemark, Finnland, Norwegen, Niederlande, Polen mit je 8 Alltagswörtern (Hallo, Danke, Bitte, Auf Wiedersehen, Ja, Nein, Wasser, Eis). Die Bedeutungen sind übersetzte Ressourcen (EN/DE), die Wörter der Landessprache sind Daten.
- **Landeserkennung:** alle ca. 10 km per Photon-Reverse-Geocoding; erster erkannter Wert setzt nur das Land, erst ein **Wechsel** (z. B. DE → DK) löst „Willkommen in Dänemark! Auf Dänisch heißt „Hallo" „Hej" …" aus. Danach spricht die App die beiden Wörter in der Landessprache (Sprachausgabe wird dafür kurz umgestellt und bei jeder Navigationsansage sofort zurückgesetzt) und stellt eine Quizfrage dazu (drei Wörter zur Wahl, 15 Punkte).
- **Quiz-Platzhalter** („lang") im Aufgabenkatalog für Altersstufen 7–14: fragt im aktuellen Land ein noch nicht gelerntes Wort ab, ohne Kurs gibt es eine Rückfall-Aufgabe.
- Grenzen: Aussprache hängt von den auf dem Handy installierten TTS-Sprachen ab (fehlt die Sprache, wird das Wort nicht gesprochen); Länderzuordnung braucht Netz; Deutsch als Zielsprache und weitere Länder (Frankreich, Italien …) fehlen noch.

## 28. Stand Erweiterung 7: Himmel & Flugzeuge

Gebaut (Build/Lint/Tests grün; Flugzeug- und Kp-Antworten gegen echte Daten vom 20.11.2026 getestet; nicht auf dem Gerät erprobt):
- **Flugzeuge (tagsüber):** adsb.lol (frei, ohne Schlüssel, Community-ADS-B): „Schaut nach oben! Ein Airbus A320 fliegt im Nordosten in etwa 10,7 km Höhe." Es werden nur Flugzeuge über 1000 m und höchstens 25 km entfernt genommen (aus den 5 nächsten gewürfelt). OpenSky ist von der Entwicklungsumgebung nicht erreichbar und wird nicht genutzt.
- **Nachts:** Sonnenstand wird lokal berechnet (Dunkelheit = Sonne > 6° unter dem Horizont). Dann entweder **Polarlicht-Chance** (NOAA SWPC Kp-Index, nur nördlich von 55° Breite; Faustregel: Rand des Ovals bei 67° − 2° je Kp; „gut" ab 3° nördlich davon) oder die **Mondphase** (lokal berechnet).
- **Quellen-Hinweis** („Daten: adsb.lol, NOAA SWPC") in der Kinderansicht.
- **Nicht umgesetzt:** ISS-Überflüge (Datenquellen erreichbar, aber Sichtbarkeitsberechnung nicht gebaut), Sternbilder/Planeten, Mondhöhe über dem Horizont.
- Hinweis zum Datenschutz: Für Flugzeuge, Tiere und Geschichten wird die aktuelle Position an die jeweiligen freien Dienste (adsb.lol, iNaturalist, Wikipedia, Overpass, Open-Meteo, Photon) gesendet; ohne Konto/Kennung, nur für den privaten Gebrauch gedacht.

## 29. Stand Erweiterung 8a: Eigene Rätsel (Eltern)

Gebaut (Build/Lint/Tests grün; nicht auf dem Gerät erprobt):
- **Rätsel-Editor** („Eigene Rätsel" in der Kinderansicht, nur im Stand): Frage, drei Antworten, richtige Antwort, Auslöser nach X km Fahrstrecke **oder** in der Nähe eines gesuchten Ortes (Radius 1,5 km). Prüfregeln (leere Felder, doppelte Antworten, Längen, fehlender Auslöser) mit übersetzten Fehlermeldungen. Gespeichert nur lokal auf dem Gerät.
- Im Spiel erscheint ein fälliges Rätsel einmal pro Fahrt als Quiz (20 Punkte), nicht in kurvigen Abschnitten und nur, wenn keine andere Aufgabe aktiv ist, mit Sprachansage.
- **Community-Rätsel** (Teilen zwischen Nutzern) sind bewusst nicht gebaut: sie bräuchten Server, Konten und Moderation (Kinderschutz) und widersprechen dem Null-Budget-Ansatz.
- **Offen: Sprachantworten** (Kinder antworten per Zuruf). Geplant nur mit Spracherkennung **auf dem Gerät** (kein Audio an Server), Abgleich der Antwort mit den Optionen; siehe nächster Schritt.

## 30. Stand Erweiterung 8b: Sprachantworten

Gebaut (Build/Lint/Tests grün; Abgleichslogik ausführlich getestet, die Spracherkennung selbst **nicht auf dem Gerät erprobt**):
- Kinder antworten per Zuruf statt Tippen: Bei Multiple-Choice-Aufgaben wird die gesprochene Antwort der Option zugeordnet (Groß-/Kleinschreibung, Umlaute, kleine Erkennungsfehler, „die zweite", mehrere Hypothesen; mehrdeutige Antworten zählen nicht). Bei „Gefunden"-Aufgaben genügen Wörter wie „gefunden/fertig/ja". Bis zu 3 Versuche; Tippen geht weiter.
- **Datenschutz:** Nur Spracherkennung **auf dem Gerät** (`createOnDeviceSpeechRecognizer`, Android 12+, Offline-Sprachpaket). Ohne diese ist der Schalter deaktiviert; es gibt **keinen Rückfall auf Cloud-Erkennung**. Das Mikrofon hört erst nach Ende der Sprachansage zu, nur während einer aktiven Aufgabe und nur bei eingeschaltetem Schalter. Berechtigung RECORD_AUDIO wird beim Einschalten angefragt.
- Grenzen: Erkennungsqualität hängt vom Offline-Sprachpaket und der Fahrgeräuschkulisse ab; Wörter in Fremdsprachen (Sprachkurs) werden mit der Gerätesprache erkannt und daher oft schlecht getroffen.
