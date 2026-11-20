# 3D-Fahrzeuge als Drehbilder

Die Fahrzeuge der Karten-Figuren sind echte 3D-Modelle von **Kenney (CC0)**, in 36 Drehbilder (je 10 Grad Fahrtrichtung) umgerechnet:
`app/src/main/assets/figures3d/<id>_NN.webp` (NN 00 = Norden, im Uhrzeigersinn).

Quellen (alle CC0, https://opengameart.org): Car Kit 3.1, Train Kit, Watercraft Kit, Space Kit.

Neu erzeugen (Headless-Chromium mit three.js, ohne Grafikkarte):
1. `npm pack three@0.160.0` und entpacken nach `$SPRITE_WORK/three`.
2. Modelle (GLB/OBJ samt `Textures/`) nach `$SPRITE_WORK/models/{car,train,water,space}/` legen.
3. `render.html` und `render.js` nach `$SPRITE_WORK`, einen Webserver auf Port 8765 starten (`python3 -m http.server 8765 --directory $SPRITE_WORK`).
4. `python3 build_sprites.py [id ...]` (Playwright muss installiert sein).

Neue Fahrzeuge: Eintrag in `MODELS` und in `Avatars.models` (`:game-engine`); der Test `everyModelHasAllRotationFrames` prueft, dass alle Bilder da sind.
