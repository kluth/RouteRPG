# Spec 001: Nahe Geräte koppeln (Bluetooth)

## Ziel
Kinder- und Beifahrer-Geräte im Auto verbinden sich mit dem Hauptgerät (Navigation) und bedienen die Aufgaben auf ihrem eigenen Gerät. Der Fahrer bekommt weiterhin nur Audio.

## Entscheidungen (mit dem Nutzer abgestimmt)
- **Transport:** Bluetooth Classic (RFCOMM) mit der Framework-API, keine Play-Services, keine proprietäre Bibliothek. Läuft ohne WLAN und ohne Internet. KiloQuest muss auf beiden Geräten installiert sein (Android).
- **Rechte:** Gekoppelte Geräte dürfen Aufgaben beantworten (Kind) und Umfahrung/Pause entscheiden (Beifahrer). Jedes Gerät hat ein eigenes Alter.
- **Nicht erlaubt für gekoppelte Geräte:** Ziel ändern, Navigation starten/stoppen, Einstellungen, Offline-Daten löschen.

## Ablauf
1. Hauptgerät: Beifahrer-Menü, "Geräte koppeln", sichtbar machen (Systemdialog) und warten.
2. Zweites Gerät: Beifahrer-Menü, "Mit Hauptgerät verbinden", Gerät in der Liste wählen (System-Kopplung mit Zahlenvergleich, erst beim ersten Mal).
3. Das zweite Gerät schickt `Hello` (Name, Alter). Das Hauptgerät zeigt die Anfrage; der Beifahrer erlaubt sie als **Kind** oder **Beifahrer** oder lehnt ab. Rechte vergibt immer das Hauptgerät.
4. Das Hauptgerät schickt Zustands-Schnappschüsse; das zweite Gerät schickt Aktionen. Entfernen jederzeit auf dem Hauptgerät.

## Protokoll (`:link-core`, rein Kotlin, getestet)
Zeilenweises JSON (UTF-8, eine Nachricht pro Zeile, höchstens 256 KB). Version 1.
- Gerät → Haupt: `hello`, `answer(choice)`, `skip`, `next`, `reroute(accept)`, `break(accept)`, `age(band)`, `ping`
- Haupt → Gerät: `welcome(role, player)`, `denied(reason)`, `snapshot`, `bye`
- Texte werden nicht übertragen, sondern Schlüssel + Argumente; jedes Gerät übersetzt in seiner Sprache (i18n-Regel). Die richtige Antwort wird erst im Feedback übertragen, nie in der offenen Aufgabe.

## Sicherheit und Datenschutz
- Nur Geräte, die der Beifahrer ausdrücklich erlaubt hat; Rechte-Prüfung auf dem Hauptgerät je Nachricht (`DeviceRoster.allows`).
- Kein Internet, keine Cloud, keine Telemetrie. Es werden nur Spielzustand und der selbst gewählte Gerätename übertragen, Position des Autos nur per Bluetooth an erlaubte Geräte (Spec 001 war veraltet).
- Höchstens 4 Kind- und 2 Beifahrer-Geräte.

## Grenzen (bewusst)
- Nur Android. Test mit mehreren echten Geräten steht aus (ohne Gerät nicht prüfbar).
- Altersstufen je Gerät gelten für die Auswahl der Aufgaben bei der nächsten Planung (vor der Fahrt) und sofort für die Darstellung auf dem Gerät.

## Erweiterung: Beifahrer-Dashboard
- Eigene Nachricht `drive` (Host -> nur Beifahrer-Geräte): Ziel, Rest-Strecke/-Zeit, Fortschritt, Tempo mit Limit, Land, nächste Anweisung.
  Grob gerundet (100 m, 1 min, 5 km/h, 50 m), höchstens alle 2 s und nur bei Änderung; der schwere Snapshot bleibt unberührt.
  Die Ankunftszeit rechnet das Beifahrer-Gerät aus der Rest-Zeit. Ältere Geräte ignorieren die unbekannte Nachricht.
- Neue Beifahrer-Aktion `break_request` („Pause jetzt vorschlagen“); Kinder dürfen sie nicht senden.
- Beifahrer-Bildschirm: Fahrtstatus-Karte, Klo-Alarm, Entscheidungen (Umfahrung, Pause), Pause-Knopf, Karte „Die Kinder“ (Punkte, Sticker,
  Fortschritt, aktuelle Aufgabe). Dieselben Karten zeigt das Beifahrer-Menü auf dem Hauptgerät.

## Nachtrag: Reise-Extras auf dem Beifahrer-Gerät (Protokoll bleibt Version 1, nur neue optionale Nachrichten)
- Hauptgerät → Beifahrer: `extras` (`ExtrasDto`): Maut-Länder, Strecken ohne Orte, Teamziel, nächste Pause, Zielwetter, Stopps für Kinder, laufender Zwischenstopp, Spiel/Hörserie, Notfall-Karte. Nur Schlüssel und Zahlen; die Texte baut das Beifahrer-Gerät in seiner Sprache. Gesendet höchstens alle 2 s und nur bei Änderung, nur an Beifahrer-Geräte.
- Beifahrer → Hauptgerät: `cmd` (Name aus `Cmds`, optional Nummer/Argument): Stopps suchen/anfahren, Pause beenden, Hörspiel starten/stoppen, Serie, Notfall, Maut vorlesen. Nur Beifahrer-Rolle, unbekannte Namen werden abgelehnt. Spiele sprechen und hören auf dem **Hauptgerät** (dort Mikrofon und Lautsprecher).
- Beifahrer-Bildschirm zeigt zusätzlich eine Karte mit Route und Position (Kartenansicht der Kinder).
