# Spec 005: Kilo-Stimme (aufgenommene Sätze)

Ziel: Kilo klingt für Kinder wie eine feste Figur. Feste Sätze werden vorab mit einer hochwertigen Stimme aufgenommen und offline
abgespielt; alles andere bleibt bei der Geräte-Sprachausgabe.

- Aufnahmen: Assets `kilo_voice/<lang>/<id>.ogg`, Index `manifest.json` (Hash des Textes -> Id). Siehe `tools/voice/README.md`.
- Abspielen: `KiloVoicePlayer` (MediaPlayer, Audio-Fokus „Navigationsansage“), nur wenn der Text exakt zu einer Aufnahme passt.
- Vorrang: Navigationsansagen stoppen Aufnahmen; Quest-Ansagen warten, solange eine Aufnahme läuft (`SpeechArbiter`).
- Neu: Lob/Trost nach Antworten (`KiloPraise`, ohne Wiederholung hintereinander), „Neuer Sticker!“, Vorlesen-Knopf der Mini-Ansicht
  nutzt die Aufnahme.
- Datenschutz/Regeln: Keine Laufzeit-Cloud, kein Tracking. Aufnahmen sind statische Assets (AGENTS.md Regel 2/3 bleiben erfüllt).
- Grenzen: Spieler-Präfix („Spieler 2 ist dran“) und dynamische Texte nutzen die Sprachausgabe; Sprachen ohne Aufnahme ebenso.
