# Spec 010: Hörspiele, Teamziel, Hörserie, adaptive Schwierigkeit (Block 2 aus Spec 009)

Alle Regeln liegen in `:game-engine` (sprachneutral, per Seed testbar); Ablauf und Texte in `:app` (`AudioGameRunner`, `NavController`).
Fahrer: nur Audio, keine Bedienung. Spracherkennung nur auf dem Gerät (wie Spec 008). Nichts wird hochgeladen.

## Kilo-Hörspiele (ohne Bildschirm)
- Wortkette, Buchstabenspiel („Ein Tier mit M“), Reimspiel, Geschichte reihum. Start per Knopf im Beifahrer-Menü oder per Sprache
  („Kilo, Wortkette“, „Kilo, spielen“, „Kilo, Spiel beenden“).
- Kilo schimpft nie: bei falschen Antworten gibt es einen Hinweis. Nach 3× Stille beendet Kilo freundlich. „Spiel beenden“ gilt auch mitten im Spiel.
- Wörter werden **nicht** gegen ein Wörterbuch geprüft (Buchstabe/Reim reicht), damit echte Wörter nie „falsch“ sind. Kilos eigene Züge kommen aus kleinen Wortlisten.
- Nach Navigationsansagen wartet das Spiel 6 s; während eines Spiels sind Quest-Ansagen und die „Kilo“-Dauererkennung pausiert.
- Ohne On-Device-Erkennung/Mikrofon geht nur die Geschichte (mit festen Pausen); die anderen Spiele sagen, was fehlt.

## Familien-Teamziel
- Ein gemeinsames Ziel pro Reise (ca. 0,5 Punkte je km, 100–1500). Alle Punkte (Quests, Spiele, Hörserie) zählen; je Aufgabe gedeckelt (15), jüngere Kinder
  zählen mehr (×1,5 / ×1,2 / ×1). Meilensteine bei 25/50/75/100 % (Kilo baut Zelt, Lagerfeuer, kocht, feiert); bei 100 % gibt es einen Sticker.
- Anzeige: Balken auf dem Kinderbildschirm und im Beifahrer-Menü.

## Kilo-Hörserie
- 6 Folgen („Der verlorene Kompass“), eine neue Folge pro Kalendertag ab der ersten gehörten. Der Zielort wird eingesetzt. Jede Folge kommt ins Tagebuch.

## Adaptive Schwierigkeit
- `FrustrationGuard` (nur diese Fahrt, nichts gespeichert): 3 Fehlschläge/Überspringen → leichtere Aufgabe; 4 richtige Quizantworten → schwerere;
  5 von 5 schlecht → ruhige Ansage plus leichte Aufgabe. Abklingzeit 4 Ergebnisse. Die Aufgabe wird nur unter den Sicherheitsbedingungen der Live-Aufgaben eingefügt
  (keine aktive Aufgabe, ≥ 600 m zur Abbiegung, ≥ 1,5 km zur nächsten geplanten Aufgabe).

## Offen / ungetestet
- Erkennung von Kinderstimmen im Auto (Lärm) ist unsicher; echte Geräte-Tests fehlen. Wortlisten sind klein (je ~100 Wörter).
- Kilo spricht die Spiele mit der Geräte-Sprachausgabe (keine aufgenommenen Clips).
