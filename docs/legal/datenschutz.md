# Datenschutz (Stand der App)

KiloQuest ist eine private Familien-App ohne Konto, ohne Werbung, ohne Tracking und ohne Analyse-SDKs. Dieser Text ist die Langfassung des In-App-Textes
(Einstellungen > Daten & Datenschutz) und muss vor einer Veröffentlichung im Play Store öffentlich erreichbar gehostet werden.

## Daten auf dem Gerät (nicht hochgeladen)
Punkte, Sticker, Einstellungen, Figuren, Reisetagebuch, Reisepläne, Fotos (`filesDir/photos`, ohne EXIF/GPS neu kodiert), Offline-Pakete.
`allowBackup=false`. Löschen: einzelne Foto-Einträge, Tagebuch komplett, Offline-Daten, Zurücksetzen. Export als ZIP (Tagebuch JSON + Fotos).
Der Server-Token liegt nur in den privaten SharedPreferences, nicht in der APK (außer bei `-PembedToken=true` für lokale Entwicklung).

## Empfänger von Anfragen (IP-Adresse + für die Anfrage nötige Koordinaten/Suchbegriffe)
| Dienst | Zweck | Lizenz/Bedingung |
|---|---|---|
| Eigener Valhalla-Server | Routen, Tempolimits, Abbiegedaten | Betreiber = Nutzer; Logging/Aufbewahrung dort festlegen |
| Photon (komoot) | Ortssuche, Land einer Position | Fair-Use, ODbL-Daten |
| Overpass API | Rastplätze, Aussichtspunkte, Spielplätze | Fair-Use, ODbL |
| OpenFreeMap | Kartenkacheln | frei, Attribution OSM/OpenFreeMap |
| Wikipedia | Geschichten, Ortsinfos | CC BY-SA, Quelle sichtbar |
| Open-Meteo | Wetter | CC BY 4.0, nicht kommerziell |
| iNaturalist | Tier-/Pflanzen-Aufgaben | Lizenz je Beobachtung |
| Autobahn-API | Verkehrsmeldungen (DE) | Datenlizenz Deutschland, Quellenvermerk |

## Bluetooth-Kopplung
Gekoppelte Geräte tauschen Spielstand, Route, Position des Autos (`pos`), Tempo und Strecke (`drive`) nur direkt per Bluetooth und nur mit erlaubten Geräten.

## Optionale Cloud-Stimme für Straßennamen
Standard aus, nur nach Warnhinweis. Dann sendet die App vor einer Fahrt die **Namen der Straßen der Route** (und die Sprache, kein Konto, keine Position, keine Kindernamen) an den vom Nutzer eingetragenen **eigenen Stimmen-Server**, der sie an ElevenLabs weiterleitet, um Aufnahmen zu erzeugen. Die Straßennamen lassen Rückschlüsse auf die Reiseroute zu. Aufnahmen liegen auf dem Handy und auf dem Server des Nutzers. Betreiber = Nutzer; die Bedingungen von ElevenLabs gelten für die dortige Verarbeitung. Für eine Veröffentlichung (Play-Familienrichtlinie, Datensicherheitsangaben) gesondert bewerten.

## Kamera, Mikrofon
Kamera nur nach Tippen auf „Foto machen“. Mikrofon für Sprachantworten, Hörspiele und optional Befehle mit „Kilo“; Erkennung standardmäßig nur auf dem Gerät (Android 12+, Offline-Sprachpaket), keine Speicherung durch KiloQuest.
**Option „Spracherkennung ohne Offline-Paket“** (Standard aus, nur nach Warnhinweis): nutzt den normalen Spracherkennungsdienst des Handys (meist Google/Hersteller). Dann kann das Audio, auch von Kindern, an dessen Server gehen und unterliegt dessen Datenschutzbedingungen. KiloQuest sendet selbst nichts. Für eine Veröffentlichung (Play-Familienrichtlinie, Datensicherheitsangaben) ist das gesondert zu bewerten.

## Offen
Eltern-Einwilligung/Altersangabe, Play-Familienrichtlinie, Foreground-Service-Deklarationen: vor einem Store-Release zu klären.
