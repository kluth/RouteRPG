# Spec 006: Reisetagebuch und Hörgeschichten

## Reisetagebuch (lokal, kein Tracking)
- Automatische Einträge während der Fahrt: Fahrtbeginn, jedes neue Land, jede Geschichte, erledigte Aufgaben mit Punkten, neue Sticker, Ankunft
  mit Strecke. Speicherung als JSON in SharedPreferences (`TravelDiary` in `:nav-core`, getestet), höchstens 1500 Einträge.
- Dubletten: dieselbe Geschichte nur einmal; dasselbe Land nicht zweimal hintereinander am selben Tag.
- Ansicht (Eltern-Bildschirm → „Reisetagebuch“): Zahlen (Tage, Kilometer, Länder mit Flaggen, Aufgaben, Punkte, Sticker, Geschichten) und alle
  Einträge Tag für Tag (neuester Tag zuerst); „Nochmal hören“ bei Geschichten; Teilen als Text; Löschen mit Rückfrage.
- Keine Positionsverfolgung: nur Ereignisse; die Position eines Eintrags wird nur gespeichert, wenn sie beim Ereignis bekannt ist.

## Hörgeschichten
- „Erzähl mir eine Geschichte“ (Kinderansicht, nicht bei offener Aufgabe): längere Wikipedia-Geschichte (bis 6 Sätze, 15 km Umkreis) zu einem Ort in
  der Nähe, in Gerätesprache, sonst Englisch; Kilo liest sie vor (Audio), der Text steht mit Lizenzhinweis (Wikipedia, CC BY-SA) auf der Karte.
- Wiederholungsbremse wie bei Geschichten-Aufgaben (`pickFresh`). Jede Geschichte landet im Tagebuch und lässt sich dort erneut anhören.
- Fahrer: nur Audio, keine Bedienaufgabe. Der Knopf liegt in der Kinderansicht, nicht im Fahrerbildschirm.

## Grenzen
- Die Kinderansicht der gekoppelten Geräte hat den Geschichten-Knopf noch nicht; Geschichten sind nicht im Offline-Paket (brauchen Netz).
- Geschichten werden mit der Geräte-Sprachausgabe vorgelesen (kein Wiedererkennungs-Hash wie bei Kilos Aufnahmen).
