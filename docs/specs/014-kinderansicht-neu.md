# 014 – Kinderansicht neu

## Ziel
Übersichtlicher, schöner und spaßiger, vor allem für Kinder von 4–6 Jahren. Die wichtigsten Funktionen sind jederzeit mit einem Tipp erreichbar.

## Prinzipien
- Ein Ding pro Bildschirm: Statuszeile (Sterne, Sticker, Fortschritt), eine große Karte in der Mitte, feste untere Leiste **Klo / Karte / Mehr**.
- Alles andere (Album, Kilo-Hörserie, Spiele, Figur, Karte, Ortsgeschichte) liegt hinter „Mehr“. Verlassen nur per Halten-Taste (Eltern-Hürde, 900 ms).
- Zielgrößen: Mini 96 dp Buttons, Explorer 64 dp, Pro 56 dp. Entprellung 400 ms.
- Mini sieht nie eine „falsch“-Meldung. Antwortoptionen haben Form und Farbe, nie nur Farbe. Kilo liest Aufgaben vor.
- Paletten pro Altersband (orange, grün, violett), hell und dunkel; WCAG-Kontrast ≥ 4,5 per Unit-Test (`KidsPaletteTest`).
- Reduzierte Bewegung: System-Animationsskala 0, Einstellung „Ruhige Ansicht“ oder Kurvenfahrt mit Reisekrankheitsmodus. Ohne Konfetti, Hüpfen und Wackeln.
- TalkBack: Überschriften, Live-Regionen, zusammengefasste Fortschrittsbeschreibung, Sticker-stateDescription.
- Kinder-Geräte können über Bluetooth Spiele und Kilo-Serie starten (`Cmds.GAME`, `STOP_GAME`, `SERIES`); Notfall bleibt dem Beifahrer vorbehalten.

## Code
`app/.../ui/kids/` (KidsStyle, Motion, KidsParts, KidsHome); `KidsScreen` delegiert dorthin. `KidsMini.kt` entfällt.

## Nachtrag: Restpunkte umgesetzt
- Hauptgerät: Quests-Knopf ab > 10 km/h ausgeblendet (Kinder nutzen ihr eigenes Gerät, Fahrer nur Audio).
- Karte: Karteninhalt nicht mehr antippbar, nur Knöpfe öffnen sie; runde Knöpfe und Figur-Feld Mini 72 dp, sonst 56 dp (`mapButtonSize`, Unit-Test).
- Alters-/Spielerwahl und Trennen kommen in der Kinderansicht nicht vor (nur Eltern-/Einstellungsbereich); Foto-Aufnahme nur im Elternbereich. Die Figur-Wahl bleibt bewusst für Kinder.
- Verbindung weg (Kinder-Gerät): Kinderansicht bleibt mit ruhigem Kilo-Hinweis „Kilo sucht das Auto“, Klo-Knopf sendet dann nichts; automatischer Neuaufbau (`UiState.linkLost`).
- Kein Mikrofon (Hauptgerät): Spielstart fragt die Erlaubnis an, bei „Nein“ ruhiger Hinweis für Erwachsene; das Geschichtenspiel läuft ohne Mikrofon.

## Nicht geprüft / offen
- Echte Geräte, TalkBack, Schriftgröße 2,0, Bluetooth-Abbruch im Auto.
