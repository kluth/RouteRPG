# Valhalla auf dem Hostinger-VPS

## Voraussetzungen
- VPS mit Ubuntu/Debian, root-Zugang per SSH. Empfehlung: **mind. 8 GB RAM und 40 GB Disk** für Deutschland (bei 4 GB RAM klappt der Tile-Bau nur mit dem Swap aus dem Skript und dauert länger). DACH/Europa braucht deutlich mehr.
- Domain/Hostname, der auf die VPS-IP zeigt (Hostinger liefert `srvXXXXXX.hstgr.cloud`), für automatisches HTTPS.

## Installation
```bash
ssh root@<vps-ip>
git clone https://github.com/kluth/RouteRPG.git && cd RouteRPG/infra/valhalla/vps
DOMAIN=srvXXXXXX.hstgr.cloud ./setup.sh
```
Das Skript installiert Docker, legt Swap an, öffnet nur Port 22/80/443 und startet Valhalla hinter Caddy. Zugriff nur mit Header `X-Api-Token` (Token steht in `/opt/kiloquest-valhalla/.env`). Anderes Land: URL(s) als Argumente übergeben.

## Test
```bash
curl -H "X-Api-Token: <TOKEN>" https://<DOMAIN>/status
curl -H "X-Api-Token: <TOKEN>" https://<DOMAIN>/route --data '{"locations":[{"lat":52.52,"lon":13.40},{"lat":48.14,"lon":11.58}],"costing":"auto"}'
```

## Hinweise
- Ungetestet gegen deinen VPS: bitte Fehlermeldungen schicken, dann passe ich es an.
- Updates der OSM-Daten: `docker compose down && rm -rf data/valhalla_tiles* && docker compose up -d` (z. B. monatlich per Cron).
- Alternative ohne öffentliche Domain: Tailscale auf VPS und Handy, Valhalla nur im Tailnet.

## Stand: Hostinger-VPS (srv1091497)
Auf dem VPS läuft bereits Traefik, daher ist dort `docker-compose.traefik.yml` deployt (Projekt `kiloquest-valhalla`, 4 GB RAM-Limit, Gebiet: NRW, Niedersachsen, Bremen, Hamburg, Schleswig-Holstein, Dänemark, Schweden – also Duisburg bis Lappland). Adresse: `https://valhalla.kluth.cloud`, Zugriff nur mit Header `X-Api-Token` (Token in der Projekt-Umgebung auf dem VPS). Der erste Start baut die Tiles, solange antwortet `/status` noch nicht.

## Fehlerbild: Server antwortet, kennt aber keine Straßen
Symptom: `/status` liefert 200, `/route` und `/locate` finden nirgends Kanten („No suitable edges near location"). Ursache war ein abgestürzter Tile-Bau (`terminate called ... std::exception`, davor `Failed tile ... vector::_M_range_check`); der Container startete danach mit einer unvollständigen `valhalla_tiles.tar` (nur 358 Kacheln). Abhilfe: Projekt samt Volume löschen und mit `docker-compose.traefik.yml` neu starten (Gebiete zuerst per `osmium merge` zu einer Datei vereinen). Prüfen: `POST /locate` für eine Stadt muss Kanten liefern.
