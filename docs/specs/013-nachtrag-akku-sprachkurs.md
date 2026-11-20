# Spec 013: Akku-Sparmodus, Speicher, erweiterter Sprachkurs, Notfallsätze

## Akku-Sparmodus (`PowerPolicy`, `PowerMonitor`)
- Einstellung „Akku-Sparmodus“: automatisch (unter 20 % ohne Laden oder wenn der Energiesparmodus von Android an ist), immer an, aus.
- Wirkung: Abfrage-Abstände ×3 (Aussichtspunkte, Wetter, Verkehr), kein Dauerzuhören auf „Kilo“, keine Foto-Bitten. Navigation, Ansagen und Tempolimit bleiben unverändert. Das Beifahrer-Dashboard zeigt „Akku-Sparmodus ist an“.
- Akkustand wird höchstens alle 30 s lokal gelesen; nichts wird gesendet.

## Speicher (`StorageUsage`, `StorageScanner`)
- Einstellungen > Akku und Speicher > Speicher: Fotos, Reise-Pakete, temporäre Dateien (Exporte, Collagen, Reisebücher); Warnung ab 300 MB Fotos oder 1,5 GB Pakete; „Temporäres löschen“ räumt nur den Cache auf (Fotos und Pakete bleiben). Offline-Kartenkacheln von MapLibre sind nicht eingerechnet.

## Erweiterter Sprachkurs und Notfallsätze
- Neue Länder im Mini-Sprachkurs: FR, IT, ES, HR, CZ (je 8 Wörter, wie bisher).
- Notfall-Karte: je Land sechs Sätze für Eltern (Arzt, Apotheke, Allergie, Kind krank, Hilfe, Krankenhaus) in der Landessprache mit Bedeutung in der App-Sprache (`ParentPhrases`, 11 Länder).
- **Die Sätze sind nicht von Muttersprachlern geprüft** (Hinweis in der Karte); vor einer Veröffentlichung prüfen lassen.

## Weitere Nachträge
- Echter Zwischenstopp in einer Route, Wikipedia-Auszug am Stopp, Live-Update (Android 16), Zielwetter für Kinder, Satelliten-SOS-Hinweis: siehe Spec 011/012.
- Aufgenommene Kilo-Stimme für die Hörspiele: siehe Spec 010 und `tools/voice/README.md`.
