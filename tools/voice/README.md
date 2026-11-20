# Kilo-Stimme (vorab erzeugte Aufnahmen)

Kilo spricht feste Sätze mit einer aufgenommenen Stimme statt mit der Geräte-Sprachausgabe. Die Aufnahmen entstehen **einmal vorab**
(ElevenLabs, Modell `eleven_v4`, Stimme „Finn Fable – Children's Stories DE/EN“, `voice_id` `yYTemNXovAWXGGqLADJf`) und liegen als kleine
Ogg-Opus-Dateien in `app/src/main/assets/kilo_voice/<de|en>/<id>.ogg`. Die App ruft **zur Laufzeit keinen Cloud-Dienst** auf, sendet
keine Daten und braucht kein Netz.

## Was wird aufgenommen
`build_lines.py` liest die Android-Strings und schreibt `lines.json`:
- alle festen Quest-Texte aus dem `QuestCatalog` (`quest_<id>`), bei Quizfragen zusätzlich die Variante für die Kleinsten mit
  Antworten (`quest_<id>.opts`: „Blauer Kreis: … Oranges Quadrat: … Grünes Dreieck: …“),
- feste Kilo-Sätze (`kilo_intro`, `kids_new_sticker`, `mini_great`, `kids_waiting`),
- Lob und Trost (`kilo_praise_1..6`, `kilo_retry_1..4`), die Kilo nach einer Antwort wechselnd sagt,
- Sätze der Hörspiele, Teamziel-Meilensteine, Anpassungs-Ansagen, Hörserien-Hinweise und die Geschichten-Anfänge/-Übergänge (alle Texte ohne Platzhalter, siehe `FIXED` in `build_lines.py`). Die Hörserien-Folgen mit Zielort und Zeilen mit Platzhaltern (z. B. „Mein Wort ist %1$s“) bleiben bei der Sprachausgabe.

Dynamische Aufgaben (Routenfakten, Wikipedia-Geschichten, Natur, Ortsnamen), Navigationsansagen und Zahlen bleiben bei der
Geräte-Sprachausgabe. Navigation hat immer Vorrang: eine Navigationsansage stoppt jede Aufnahme sofort.

## Wie findet die App die Aufnahme
Über einen Hash des gesprochenen Textes (`manifest.json`, `VoiceIndex` in `:game-engine`). Ändert sich ein Text in `strings.xml`,
passt der Hash nicht mehr: die App fällt automatisch auf die Sprachausgabe zurück, und der Test `KiloVoiceTest` meldet die veraltete
Aufnahme.

## Neu erzeugen oder erweitern
1. Text in `values/strings.xml` und `values-de/strings.xml` ändern oder ergänzen, neue Quest-Ids stehen im `QuestCatalog`.
2. `python3 tools/voice/build_lines.py` (aktualisiert `lines.json` und das Manifest).
3. Fehlende Zeilen mit dem gleichen `voice_id`/Modell erzeugen (je Zeile eine Generierung, ~1 Credit pro Zeichen) und als
   `<id>.<lang>.mp3` in einen Ordner laden.
4. `tools/voice/encode.sh <Ordner>` (Stille am Rand entfernt, -18 LUFS, Mono, Opus 32 kbit/s) legt die Dateien in die Assets.
5. `python3 tools/voice/build_lines.py` erneut, dann `./gradlew :game-engine:test` (prüft Dateien und Hashes).

## Lizenz und Kosten
Die Aufnahmen sind synthetische Sprache, erzeugt mit dem ElevenLabs-Konto des Projektinhabers. Die zulässige Nutzung (insbesondere
kommerziell oder bei einer Veröffentlichung im Play Store) richtet sich nach den Bedingungen des jeweiligen ElevenLabs-Tarifs zum
Zeitpunkt der Erzeugung; vor einer Veröffentlichung prüfen. Verbrauch für 53 Zeilen je Sprache: rund 2.700 Zeichen pro Sprache, zusammen etwa 5.400 Credits (Deutsch und Englisch). Die 33 weiteren Zeilen je Sprache (Hörspiele u. a.) brauchten rund 3.900 Zeichen, zusammen etwa 3.900 Credits.
