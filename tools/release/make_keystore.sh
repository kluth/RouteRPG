#!/bin/bash
# Erzeugt einen Upload-Schluessel fuer Google Play (Play App Signing verwaltet den eigentlichen App-Schluessel).
# Auf einem Rechner oder in einer Cloud-Shell (z. B. GitHub Codespaces) ausfuehren. Die Passwoerter werden abgefragt, nie ins Repo schreiben.
# Danach: Datei sicher aufbewahren (Passwortmanager) und die vier GitHub-Secrets setzen (siehe docs/release/PLAY_TEST.md).
set -euo pipefail
OUT="${1:-kiloquest-upload.jks}"
ALIAS="${2:-kiloquest-upload}"
keytool -genkeypair -v -keystore "$OUT" -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10000
echo
echo "Fertig: $OUT (Alias: $ALIAS)"
echo "Secret KILO_KEYSTORE_B64 (eine Zeile) erhaeltst du mit:  base64 -w0 $OUT"
echo "Weitere Secrets: KILO_KEYSTORE_PASSWORD, KILO_KEY_ALIAS ($ALIAS), KILO_KEY_PASSWORD"
