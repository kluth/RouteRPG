# AGENTS.md – Regeln für KI-Agenten in KiloQuest

KiloQuest: Familien-Navi (Android + Android Auto) mit Schnitzeljagd für Kinder 4–14. Nur freie Datenquellen. Plan: `docs/ARCHITEKTUR_UND_ROADMAP.md`.

## Build & Test
- Android SDK: `ANDROID_HOME=/opt/android-sdk` (oder `local.properties` mit `sdk.dir=`), JDK 21.
- Alles prüfen: `./gradlew :game-engine:test :nav-core:test :app:assembleDebug :app:lintDebug`
- Ein Änderungs-PR muss diese drei Befehle grün haben.

## Module
- `:app` – Android-App (Compose, MapLibre). Später `:feature-car` (Car App Library).
- `:nav-core` – reines Kotlin/JVM: Geometrie, Polyline-Decoder, Valhalla-Request/Parser, Guidance-Engine, Sprachansage-Planer. Keine Android-Abhängigkeiten.
- `:game-engine` – reines Kotlin/JVM, **keine Android-Abhängigkeiten**, sprachneutral. Muss unit-testbar bleiben.

## Regeln
1. **i18n:** Keine hartcodierten Texte. Strings in `values/` (Englisch, Standard) und `values-de/`. Quests tragen nur `textKey`, nie Text. Lint `HardcodedText` ist Fehler.
2. **Freie Daten/Lizenzen:** Keine proprietären SDKs/APIs (Google Maps, Mapbox, HERE …). Neue Abhängigkeit nur mit Lizenzprüfung (Apache/MIT/BSD ok; GPL/AGPL nur nach Rückfrage). Attribution (OSM, OpenFreeMap) bleibt sichtbar.
3. **Kinder & Datenschutz:** Keine Werbung, keine Tracking-/Analytics-SDKs, Crowd-Daten nur opt-in und anonymisiert.
4. **Fahrsicherheit:** Nie Quests an Kreuzungen/Ausfahrten auslösen; Fahrer bekommt nur Audio, keine Bedienaufgaben. Car-App-Screens nur über erlaubte Templates.
5. **Blitzerwarnung:** Nur über Länder-Policy (Standard AUS), siehe Plan 12.4.
6. **Tests:** Jede Logik in `:game-engine` bekommt Unit-Tests. Deterministisch (Seeds).
7. **Kleine Schritte:** Eine Aufgabe = ein Branch/PR. Vorher kurze Spec in `docs/specs/NNN-name.md` bei größeren Features.
8. **Nicht tun:** Keine Secrets committen, keine Binärdaten (`*.pbf`, `*.pmtiles`), keine Tests auskommentieren.
