# 016 – Vollständige Stimme (möglichst alles mit ElevenLabs)

Status: Entwurf. Schritt 1 (Messung) für NRW erledigt, Ergebnis in `016-messung.md` (Top 5.000 Namen = 50 % der Wegabschnitte, 20.000 = 74 %, ~97.000 verschiedene Namen: Pro-Fahrt-Paket bleibt der Kern). Der Rest wartet auf die Entscheidungen unten.

## Ziel
Kilo (und optional die Navigation) spricht möglichst alles mit aufgenommener Stimme statt Geräte-Stimme: feste Sätze, Zahlen, Abbiegeansagen mit Straßennamen, Orte, Vornamen der Kinder, Geschichten. Ohne Netz im Auto, ohne dass Orte oder Kindernamen unbemerkt an einen Cloud-Dienst gehen (AGENTS.md Regeln 2 und 3).

## Warum keine reinen Listen
Straßennamen sind ein Long-Tail: allein in NRW 96.838 verschiedene Namen, die häufigsten 5.000 decken 50 % der Wegabschnitte (Messung in `016-messung.md`; nur NRW und Abschnitte statt Ansagen). Nachnamen kommen in der App nicht vor; Vornamen nur bei den Spielern; Ortsnamen oft (Ziel, Stopps, Geschichten, Unterkünfte).

## Strategie: vier Stufen mit Rückfall
1. **Bausteine in der App (~400 Clips):** Abbiegeverben, Entfernungen, Zahlen, Kreisverkehr-Ausfahrten, „dann“, Autobahn-Begriffe. Sätze werden aus Bausteinen zusammengesetzt (wie `VoiceParts` für Tempolimit/Wetter).
2. **Namenpakete zum Nachladen (pro Land, nicht in der APK):** häufigste Straßennamen und Stämme, größte ~2.000 Orte der Reiseländer, ~500 Vornamen. Offene Quellen: OSM (ODbL, Attribution), Wikidata (CC0), offene Vornamenstatistiken von Städten (Lizenz je Datensatz prüfen).
3. **Pro-Fahrt-Stimmenpaket (Kern):** Beim Planen (online, wie das Offline-Paket) ermittelt die App die angesagten Namen der Route, die noch keinen Clip haben, lässt sie erzeugen und cached sie. Grobe Größe: 30–80 Namen (~1.000 Zeichen) je Fahrt.
4. **Live-Erzeugung für Langtexte (optional, opt-in):** Wikipedia-Geschichten, Tagebuch. Teuer (~600 Zeichen je Geschichte).
5. **Rückfall:** Fehlt auch nur ein Baustein, spricht die Geräte-Stimme den **ganzen Satz** (nie mitten im Satz die Stimme wechseln).

## Architektur
- `:nav-core`: `SpokenPlan` – baut aus Manöver (Typ, Distanz, Straßennamen) eine Liste von Slots (Baustein-Ids und Namen); reine Logik, deterministische Tests.
- `:game-engine`/`:app`: `KiloVoicePlayer.playParts` (vorhanden) spielt Slots; Manifest je Paket; Cache-Verzeichnis `filesDir/voice_cache/`.
- Server-Proxy (eigener Server): hält den ElevenLabs-Schlüssel (**nie in der APK**), cached nach Text-Hash, erzeugt jeden Satz einmal, Token/Rate-Limit wie beim Routenserver, kein Logging von Inhalten.
- Zwei Stimmen: Kilo (Finn Fable) und eine nüchterne Navi-Stimme (noch zu wählen), getrennt halten.

## Datenschutz und Lizenz
- Cloud-Erzeugung nur mit **opt-in und Warnhinweis**; Standard bleibt Geräte-Stimme.
- Kindernamen einzeln ohne Kontext erzeugen oder aus dem Namenpaket nehmen; nie Standort/Position mitsenden.
- ElevenLabs-Tarif: Nutzungsrechte für die Verteilung der Clips in einer veröffentlichten App vor einem Store-Release prüfen.

## Kosten und Größe (Größenordnung, nicht gemessen)
- Mittlere Clip-Größe ~9 KB (Opus 32 kbit/s): 10.000 Clips ≈ 90 MB → Pakete zum Nachladen.
- Bisher 1 Credit je Zeichen; 10.000 Namen à ~12 Zeichen ≈ 120.000 Credits (Monatskontingent 363.000); Preis zusätzlicher Credits unbekannt.
- Pro Fahrt (Stufe 3) ~1.000 Zeichen.

## Schritte
1. **Messung** (`tools/voice/measure_names.py`): Häufigkeit der Straßen-/Ortsnamen aus OSM, Abdeckungskurve „Top-N → Anteil der Straßenabschnitte/Namen“, Stämme/Suffixe. Ergebnis in `docs/specs/016-messung.md`.
2. `SpokenPlan` + Tests, Navigation darauf umstellen (Rückfall ganzer Satz).
3. Bausteinpaket aufnehmen.
4. Proxy + Pro-Fahrt-Paket, danach Namenpakete.
5. Qualitätstest (Zufallsproben per Spracherkennung gegenprüfen) + Hörtest im Auto.

## Stand Schritt 2 (Satzbaukasten, umgesetzt)
- `nav/SpokenPlan.kt`: baut Abbiegeansagen aus Bausteinen (`In 300 Metern links abbiegen auf die Hauptstraße.`), DE und EN; Slots = aufgenommene Bausteine (`NavVocab`, 36 Ids in `tools/voice/nav_vocab.json`) und Namen. Ungewöhnliche Fälle (Start, Ziel, unbekannte Typen, Kreisverkehr ohne bekannte Ausfahrt) und andere Sprachen liefern den Text von Valhalla.
- Deutsche Straßennamen: Artikel (die/den/das) nach Endung geraten; bei Unsicherheit („Am Markt“) wird der Name weggelassen statt falsch gesprochen.
- `AnnouncementPlanner.nextSpoken` und `KiloVoicePlayer.playSlots` (Bausteine aus Assets, Namen aus `filesDir/voice_cache/<lang>/<hash>.ogg`): Sätze nur mit aufgenommenen Clips, wenn ALLE Teile vorhanden sind, sonst ganzer Satz per Sprachausgabe.
- Einstellung „Kurze Abbiegeansagen“, Standard **an** (abschaltbar); der zusammengesetzte Text ist kürzer als Valhalla-Anweisungen.
- **Aufgenommen:** die 36 Bausteine je Sprache in Kilos Stimme (Entscheidung: Navigation = Kilo; 1.613 Zeichen). Fehlt der Name-Clip (bisher immer, bis das Pro-Fahrt-Paket existiert), entfällt der Name samt „auf die/den/das“ (`dropMissingNames`), Kilo spricht dann ohne Straßenname. Fehlt ein Baustein, spricht die Geräte-Stimme den ganzen Satz (mit Namen).
## Stand Stufe 3 (Pro-Fahrt-Namenpaket, umgesetzt; Cloud-Anbindung vom Nutzer freigegeben, opt-in)
- `nav/VoicePack.kt`: `neededNames` (Name-Slots der Route ohne Clip, ohne Duplikate, max. 150), `nameHash` (= `VoiceIndex.hash`, Test im App-Modul).
- App: `VoiceCloud.kt` (URL/Token zur Laufzeit, nur https), `NavController.prepareVoicePack` (beim Einrichten der Route und beim Einschalten; 3 parallel; Fortschritt in der Routenvorschau), Ablage `filesDir/voice_cache/<lang>/<hash>.mp3`, Player spielt Namen von dort, fehlt ein Name, entfällt er.
- Einstellungen: „Stimmen-Server“, „Kilo spricht Straßennamen (Cloud)“ (Standard aus, Warnhinweis).
- `tools/voice-proxy/`: Node-Proxy mit Token, Namensfilter, Cache, Tageslimit, Rate-Limit; siehe README dort.
- **Nicht getestet:** Proxy nur gegen simulierten Upstream; echte ElevenLabs-REST-API (Modellname `eleven_v4`?), Netz im Auto, Kosten je Fahrt, Klang der zusammengesetzten Sätze mit Namen. Deployment und Schlüssel sind Aufgabe des Nutzers.
- Noch offen: Ortsnamen (Ziel, Stopps), Kindernamen, Wikipedia-Geschichten über den Proxy; Aufräumen/Löschen des `voice_cache` in der Speicheranzeige.

## Offene Entscheidungen (Nutzer)
- Server-Proxy mit Cloud-Anbindung akzeptabel (opt-in)?
- Navigation in zweiter ElevenLabs-Stimme (statt Geräte-Stimme)?
- Aussprache fremder Namen (DK/SE/CZ): Stichprobentest vor der Massenerzeugung.

## Ungetestet
Alles hier ist Planung; Abdeckung, Kosten und Klang sind Annahmen, bis Messung und Stichproben vorliegen.
