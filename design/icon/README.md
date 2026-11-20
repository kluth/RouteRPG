# KiloQuest App-Icon

Motiv: Orts-Pin (Navigation) mit Schatzstern (Schnitzeljagd) auf blauem Verlauf mit geschwungener Straße. Farben passend zur App
(Routenblau `#1768E8`→`#0A2A5C`, Pin `#FFC83A`→`#FF7A00`, Stern `#FFD54A`). Alles wird aus **einer** Quelle erzeugt: `python3 tools/icon/build_icon.py`
(Abhängigkeiten: `cairosvg`, `Pillow`). Nichts von Hand in den erzeugten Dateien ändern, sondern das Skript anpassen.

## Was liegt wo
| Ort | Inhalt |
|---|---|
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher*.xml` | Adaptives Icon (Hintergrund, Vordergrund, **einfarbig** für „Themed Icons“ ab Android 13) |
| `app/src/main/res/drawable/ic_launcher_{background,foreground,monochrome}.xml` | Vektor-Ebenen (108 dp, sichere Zone 66 dp) |
| `app/src/main/res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher{,_round}.png` | Klassische PNG-Icons 48/72/96/144/192 px, quadratisch (abgerundet) und rund |
| `store/play/icon-512.png` | Play-Store-Symbol, 512×512, quadratisch, ohne eigene Rundung (Google maskiert selbst) |
| `design/icon/*.svg` | Quellen: Gesamtbild (Vollbild, abgerundet, rund), Hintergrund, Vordergrund, einfarbig |
| `design/icon/png/{square,rounded,round}/kiloquest-<px>.png` | 16, 32, 48, 64, 72, 96, 128, 144, 192, 256, 384, 512, 1024 px |
| `design/icon/png/kiloquest-monochrome-432.png` | Vorschau der einfarbigen Ebene |
| `design/icon/kiloquest-512.webp`, `kiloquest.ico` | WebP und Windows/Web-ICO (16–256 px) |

## Hinweise
- Der sichtbare Bereich eines adaptiven Icons ist 72 von 108 dp; die PNG/SVG-Gesamtbilder zeigen genau diesen Bereich (viewBox `18 18 72 72`).
- Das Icon ist eine eigene Gestaltung (keine Marken, keine Fremdgrafiken, Lizenz wie das Projekt).
- Vor dem Store-Upload auf echten Geräten mit verschiedenen Masken (rund, Squircle, Tropfen) und im Dunkelmodus prüfen.
