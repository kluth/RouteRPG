#!/usr/bin/env bash
# KiloQuest: Valhalla auf einem Ubuntu/Debian-VPS einrichten.
# Nutzung (als root):  DOMAIN=srv123456.hstgr.cloud ./setup.sh [TILE_URL ...]
# Standard-Region: Deutschland. Mehrere URLs durch Leerzeichen trennen.
set -euo pipefail

: "${DOMAIN:?DOMAIN setzen, z.B. DOMAIN=srv123456.hstgr.cloud}"
TILE_URLS="${*:-https://download.geofabrik.de/europe/germany-latest.osm.pbf}"
DIR=/opt/kiloquest-valhalla

# Docker installieren, falls nicht vorhanden
if ! command -v docker >/dev/null; then
  curl -fsSL https://get.docker.com | sh
fi

# Swap als Puffer fuer den Tile-Bau (nur wenn noch keiner existiert)
if [ "$(swapon --show | wc -l)" -eq 0 ]; then
  fallocate -l 8G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

mkdir -p "$DIR/data"
cp "$(dirname "$0")/docker-compose.yml" "$(dirname "$0")/Caddyfile" "$DIR/"
cd "$DIR"

# Token einmalig erzeugen und wiederverwenden
if [ ! -f .env ]; then
  API_TOKEN=$(head -c 32 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 40)
  printf 'DOMAIN=%s\nAPI_TOKEN=%s\nTILE_URLS=%s\n' "$DOMAIN" "$API_TOKEN" "$TILE_URLS" > .env
  chmod 600 .env
fi

# Firewall: nur SSH, HTTP, HTTPS
if command -v ufw >/dev/null; then
  ufw allow OpenSSH && ufw allow 80/tcp && ufw allow 443/tcp && ufw --force enable
fi

docker compose up -d
echo
echo "Fertig. Der erste Start baut die Routing-Tiles (Deutschland: ca. 1-3 Std., Fortschritt: docker compose logs -f valhalla)."
echo "Test:   curl -H \"X-Api-Token: \$(grep API_TOKEN .env | cut -d= -f2)\" https://$DOMAIN/status"
echo "Token:  $(grep API_TOKEN .env | cut -d= -f2)   (nur lokal speichern, nicht ins Repo!)"
