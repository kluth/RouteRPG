# KiloQuest

Familien-Navigation für Android und Android Auto: Die Fahrstrecke wird zur Schnitzeljagd für Kinder (4–14). Nur freie Datenquellen (OpenStreetMap, Valhalla, OpenFreeMap, Wikipedia/Wikidata, offene Verkehrsdaten).

Status: Phase 0 (Fundament). Plan: [docs/ARCHITEKTUR_UND_ROADMAP.md](docs/ARCHITEKTUR_UND_ROADMAP.md) · Regeln für Agenten: [AGENTS.md](AGENTS.md)

## Entwickeln
```bash
./gradlew :game-engine:test :nav-core:test :app:assembleDebug :app:lintDebug
```
Debug-APK: `app/build/outputs/apk/debug/app-debug.apk` (per `adb install` aufs Handy).

## Server-Zugang (nicht im Repo)
In `local.properties` (gitignored):
```
valhalla.url=https://valhalla.kluth.cloud
valhalla.token=<API-Token>
```

## Routing lokal (optional, ungetestet)
`infra/valhalla/docker-compose.yml` startet Valhalla mit einem Geofabrik-Extrakt. Für den privaten Betrieb ohne Kosten: eigenes Gerät/PC oder ein Always-Free-Server.
