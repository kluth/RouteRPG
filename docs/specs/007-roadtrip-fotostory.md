# Spec 007: Roadtrip-Fotostory

Ziel: Der Beifahrer wird in zufälligen Abständen oder bei schöner Gegend um ein Foto gebeten, immer mit beiden Kameras (Aussicht und Selfie).
Entscheidungen des Nutzers: Beifahrer-Gerät fotografiert; Auslöser „schöne Orte plus zufällig“; Ergebnis im Reisetagebuch, als Tages-Collage und
zusätzlich in der Galerie; bei Geräten ohne gleichzeitige Kameras schnell nacheinander.

## Ablauf
1. Das Hauptgerät entscheidet (`PhotoPrompter`, `:nav-core`, getestet): nach 5 min Aufwärmzeit; bei schöner Gegend (Score ≥ 3) frühestens 15 min
   nach der letzten Bitte, sonst zufällig alle 45–90 min (mindestens 30 min Abstand); höchstens 8 Bitten pro Kalendertag; 20 min Pause nach „Später“.
   Keine Bitte bei Abbiegung in unter 1 km, Klo-Alarm, Umfahrungs- oder Pausenvorschlag, Abweichung von der Route.
2. Schöne Gegend (`ScenicFinder`): alle 3 min Overpass-Abfrage im Umkreis von 1,5 km nach `tourism=viewpoint`, Gipfel, Gletscher, Wasserfall, Strand,
   Bucht, Küste, benannten Gewässern, Burgen. Nur mit Beifahrer-Gerät und Netz.
3. Nur an gekoppelte **Beifahrer-Geräte** (`photo_request`); der Fahrer wird nie gefragt. Der Beifahrer kann jederzeit selbst „Foto-Moment jetzt“ drücken
   (auch im Beifahrer-Menü des Hauptgeräts).
4. Aufnahme auf dem Beifahrer-Gerät: Kamera-Erlaubnis, Countdown 3-2-1, dann `DualPhotoCapture` (CameraX): gleichzeitig (ConcurrentCamera, Android 11+,
   nur auf unterstützten Geräten), sonst Rück- dann Frontkamera kurz hintereinander.
5. Speicherung: nur im privaten App-Speicher (`filesDir/photos`, längste Kante 1600 px, nach EXIF gedreht); optional Kopie in `Pictures/KiloQuest`
   (Android 10+). Tagebuch-Eintrag `PHOTO` mit Ort und Dateinamen auf dem Beifahrer-Gerät.
6. Tages-Collage: pro Foto-Moment eine Zeile (links Aussicht, rechts Selfie), höchstens 5 Zeilen je Bild, Teilen als JPEG; nichts wird hochgeladen.

## Datenschutz
Kamera nur auf Tipp des Beifahrers; keine Fotos im Hintergrund; keine Cloud; „Tagebuch löschen“ löscht auch alle Fotos; Datenschutz-Text aktualisiert.

## Grenzen (v1)
- Keine Kamera-Vorschau (Countdown statt Sucher); auf echten Geräten zu prüfen, vor allem gleichzeitige Kameras und Fokus.
- Die Fotos liegen im Tagebuch des Beifahrer-Geräts, nicht auf dem Hauptgerät (keine Übertragung per Bluetooth).
- Ohne gekoppeltes Beifahrer-Gerät fragt die App nicht von selbst (nur der Knopf „Foto-Moment jetzt“).
