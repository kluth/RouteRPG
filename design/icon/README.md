# KiloQuest App-Icon

Motiv: Orts-Pin (Navigation) mit Schatzstern (Schnitzeljagd) auf blauem Verlauf mit geschwungener Straße. Alles wird aus **einer** Quelle erzeugt:
`python3 tools/icon/build_icon.py` (Abhängigkeiten: `cairosvg`, `Pillow`). Nichts von Hand in den erzeugten Dateien ändern, sondern das Skript anpassen.
Übersichtsbilder: `preview-shapes.png`, `preview-colors.png`, `preview-themed.png`.

## Themes (Farbvarianten und Systemthema)
| Variante | Palette |
|---|---|
| `default` Klassisch blau | Routenblau → Marineblau, orangener Pin, goldener Stern |
| `night` Nacht | fast schwarzer Verlauf, hellerer Pin (für dunkle Startbildschirme) |
| `sunset` Sonnenuntergang | Orange → Violett, heller Pin |
| `forest` Wald | Grün-Verlauf, orangener Pin |
| `mono` Graphit | Graustufen mit weißem Pin |

- **In der App wählbar** (Einstellungen > Darstellung > App-Icon): jede Variante ist ein `activity-alias` im Manifest (`IconDefault`, `IconNight`, …); genau einer ist aktiv und trägt den Launcher-Eintrag. Der Wechsel kann den Startbildschirm kurz neu laden.
- **Themed Icons (Android 13+):** alle Varianten nutzen dieselbe einfarbige Ebene `ic_launcher_monochrome`; das System färbt sie mit den Wallpaper-Farben (`preview-themed.png` zeigt Beispiele in Hell und Dunkel).
- **Dunkelmodus im Web:** `favicon.svg` wechselt per `prefers-color-scheme` zwischen klassisch und Nacht.
- **Start-Bildschirm (Android 12+):** Marken-Hintergrund `#0A2A5C` mit der Vordergrund-Ebene (`values-v31/themes.xml`).
- **Benachrichtigungen:** `ic_stat_kiloquest` (weiß, Pin mit Stern-Ausschnitt).

## Formen
`square`, `soft-square`, `rounded`, `squircle`, `circle`, `teardrop`, `pill`, `hexagon`, `cookie`: SVG in `svg/`, PNG in `png/<form>/` (`square`, `rounded`, `circle` in 16–1024 px, die übrigen in 48/96/192/512/1024 px).
Launcher maskieren adaptive Icons selbst (rund, Squircle, Tropfen …); die Formen sind für Store, Web, Dokumente und andere Plattformen.

## Was liegt wo
| Ort | Inhalt |
|---|---|
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher*.xml` | Adaptive Icons je Variante (mit einfarbiger Ebene) |
| `app/src/main/res/drawable/ic_launcher[_<variante>]_{background,foreground}.xml`, `ic_launcher_monochrome.xml` | Vektor-Ebenen (108 dp, sichere Zone 66 dp) |
| `app/src/main/res/mipmap-*dpi/ic_launcher{,_round}.png` | Klassische PNG-Icons 48/72/96/144/192 px |
| `app/src/main/res/drawable-nodpi/icon_preview_<variante>.png` | Vorschau in der Auswahl |
| `store/play/icon-512.png` | Play-Store-Symbol, 512×512, quadratisch ohne eigene Rundung |
| `design/icon/themes/<variante>/{square,rounded,circle}/` | Farbvarianten in 48/96/192/512/1024 px, dazu SVG |
| `design/icon/png/monochrome/` | einfarbige Ebene schwarz und weiß (432 px) |
| `design/icon/web/icon-{180,192,512}.png`, `favicon.svg`, `kiloquest.ico`, `kiloquest-512.webp` | Web, Apple-Touch, Windows |

## Hinweise
- Sichtbarer Bereich eines adaptiven Icons: 72 von 108 dp; die Gesamtbilder zeigen genau diesen Bereich (viewBox `18 18 72 72`).
- Eigene Gestaltung (keine Marken, keine Fremdgrafiken).
- Vor dem Store-Upload auf echten Geräten mit verschiedenen Masken und im Dunkelmodus prüfen; die Wirkung der Themed Icons hängt vom Launcher ab.
