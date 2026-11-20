#!/bin/bash
# Wandelt rohe Aufnahmen (<id>.<lang>.mp3) in kleine, gleich laute Ogg-Opus-Dateien um und legt sie in die App-Assets.
# Aufruf: tools/voice/encode.sh <Ordner mit mp3>
# - Stille am Anfang und Ende wird entfernt (kurzer Rand bleibt), Lautheit auf -18 LUFS, Mono, 32 kbit/s Opus, 24 kHz.
set -euo pipefail
RAW="${1:?Ordner mit mp3-Dateien angeben}"
OUT="$(cd "$(dirname "$0")/../.." && pwd)/app/src/main/assets/kilo_voice"
for f in "$RAW"/*.mp3; do
  base="$(basename "$f" .mp3)"      # <id>.<lang>
  lang="${base##*.}"
  id="${base%.*}"
  mkdir -p "$OUT/$lang"
  ffmpeg -v error -y -i "$f" \
    -af "silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.05,areverse,silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.12,areverse,loudnorm=I=-18:TP=-1.5:LRA=7" \
    -ac 1 -ar 24000 -c:a libopus -b:a 32k -application voip "$OUT/$lang/$id.ogg"
done
echo "fertig: $(find "$OUT" -name '*.ogg' | wc -l) Dateien, $(du -sh "$OUT" | cut -f1)"
