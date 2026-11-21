# Kilo-Stimmen-Proxy

Kleiner Server für die optionale **Cloud-Stimme** (Spec 016, Stufe 3): Die App schickt vor einer Fahrt die Straßennamen der Route (nur Name + Sprache), der Proxy erzeugt sie mit ElevenLabs in Kilos Stimme, speichert sie nach Hash und liefert mp3. Der ElevenLabs-Schlüssel liegt **nur auf dem Server**, nie in der App.

- Keine Abhängigkeiten, Node.js ≥ 18: `node server.js` (Tests: `node --test`).
- Endpunkte: `POST /tts` (`{"text":"Hauptstraße","lang":"de"}`, Header `X-Api-Token`), `GET /health`.
- Angenommen werden nur Namen: Buchstaben, Ziffern, Leerzeichen und `- . ' &`, höchstens 40 Zeichen. Sprachen `de` und `en`.
- Inhalte werden nie geloggt, nur Zähler.

## Umgebungsvariablen
| Variable | Pflicht | Bedeutung |
|---|---|---|
| `VOICE_TOKEN` | ja | gemeinsames Geheimnis, wird in der App unter „Stimmen-Server“ eingegeben |
| `ELEVEN_API_KEY` | ja | ElevenLabs-API-Schlüssel (im ElevenLabs-Konto erzeugen) |
| `ELEVEN_VOICE_ID` | nein | Standard `yYTemNXovAWXGGqLADJf` (Finn Fable) |
| `ELEVEN_MODEL` | nein | Standard `eleven_v4`; **ob dieser Name für die REST-API gilt, ist nicht geprüft** (beim ersten Lauf kontrollieren, sonst z. B. `eleven_multilingual_v2`) |
| `DAILY_CHAR_LIMIT` | nein | Tageslimit in Zeichen (Standard 20000) schützt vor unerwarteten Kosten |
| `CACHE_DIR`, `PORT`, `MAX_TEXT_LENGTH`, `RATE_PER_MINUTE` | nein | siehe `server.js` |

## Betrieb
1. ElevenLabs-API-Schlüssel erzeugen. **Nie in den Chat oder ins Repo schreiben**, nur als Umgebungsvariable beim Hosting setzen.
2. Einen langen zufälligen `VOICE_TOKEN` wählen.
3. `tools/voice-proxy` auf einem Node.js-Hosting betreiben (eigener Server oder Node-Hosting; **https ist Pflicht**, die App akzeptiert nur `https://`).
4. In der App: Einstellungen › Stimmen-Server (Adresse + Token), dann „Kilo spricht Straßennamen (Cloud)“ einschalten und den Hinweis bestätigen.
5. Prüfen: `GET <Adresse>/health` liefert `{"ok":true,...}`.

## Datenschutz und Kosten
- Gesendet wird nur der Straßenname und die Sprache. Keine Position, keine Kindernamen.
- Aufnahmen liegen im `CACHE_DIR` des Servers und auf dem Handy (`filesDir/voice_cache`). Jeder Name wird je Sprache nur einmal erzeugt.
- Kosten: ca. 1 Credit je Zeichen; eine Fahrt braucht grob 30–80 Namen (Schätzung, nicht gemessen).
