# Bildquellen und Lizenzen (UI)

- **Kilo (Fuchs) und Tier-Sticker** (`app/src/main/res/drawable-nodpi/kilo_fox.png`, `sticker_*.png`): Noto Emoji (Google), https://github.com/googlefonts/noto-emoji. Dort sind Bilder unter Apache 2.0, die Schrift unter OFL angegeben. **Vor Veroeffentlichung die Lizenz genau dieser Dateien gegenpruefen** und ggf. Attribution in die App aufnehmen.
- **Schriften:** Atkinson Hyperlegible (Braille Institute, OFL 1.1, Datei in diesem Ordner), Nunito (OFL 1.1).
- **Symbole:** Material Symbols (Apache 2.0).
- **Karte:** (c) OpenStreetMap-Mitwirkende, OpenFreeMap, MapLibre (bleibt in der App sichtbar).
- **3D-Fahrzeuge der Karten-Figuren** (`app/src/main/assets/figures3d/`): Modelle von Kenney (https://kenney.nl), Lizenz **CC0** (Public Domain): Car Kit 3.1, Train Kit, Watercraft Kit, Space Kit, geladen von OpenGameArt. In Drehbilder umgerechnet (siehe `tools/sprites/README.md`). Eine Namensnennung ist bei CC0 nicht noetig, wird aber gern gegeben: "3D-Fahrzeuge von Kenney.nl".
- **Weitere 3D-Figuren:** Kenney Cube Pets und Mini Characters 2.0 (Tiere, Personen) sowie Blocky Characters (Helden, Ninja, Agent), alle CC0. **Flugzeuge** von OpenGameArt, beide CC0: "An-2 Airplane Lowpoly" (mokalux) und "Toy Airplane Lowpoly". Quellseiten: https://opengameart.org/content/an-2-airplane-lowpoly und https://opengameart.org/content/toy-airplane-lowpoly. Nicht verwendet: Hubschrauber-OBJ (zu grob), Modelle mit CC-BY/CC-BY-SA.

## Bibliotheken (Ergänzung)
- AndroidX WorkManager 2.9.1: Apache-2.0, für Reise-Erinnerungen (lokal, keine Server, kein Tracking).

## Sprachaufnahmen (Ergänzung)
- `app/src/main/assets/kilo_voice/`: vorab erzeugte synthetische Sprache (ElevenLabs, Stimme „Finn Fable – Children's Stories DE/EN“),
  siehe `tools/voice/README.md`. Nutzungsrechte richten sich nach dem ElevenLabs-Tarif des Projektinhabers (vor einer Veröffentlichung prüfen).
- AndroidX CameraX 1.4.1 (camera-core, camera-camera2, camera-lifecycle): Apache-2.0, für die Roadtrip-Fotostory (beide Kameras); Fotos bleiben lokal.
