#!/usr/bin/env python3
"""Erzeugt das KiloQuest-App-Icon (alle Groessen und Formate) aus einer einzigen Quelle.

Motiv: Orts-Pin (Navi) mit Schatzstern (Schnitzeljagd) auf blauem Verlauf mit geschwungener Strasse.
Ausgabe:
  - Android: adaptive Icon (Vordergrund, Hintergrund, einfarbig fuer "Themed Icons"), Mipmap-PNG (mdpi..xxxhdpi, quadratisch und rund)
  - design/icon/: SVG-Quellen, PNG in vielen Groessen (quadratisch, rund, abgerundet), WebP, ICO
  - store/play/icon-512.png fuer die Play-Store-Eintragung
Abhaengigkeiten: cairosvg, Pillow. Aufruf: python3 tools/icon/build_icon.py
"""
import math
from pathlib import Path
import cairosvg
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res"
OUT = ROOT / "design/icon"
PLAY = ROOT / "store/play"

# ---- Farben (passend zur App: Routenblau, Zielorange)
BLUE_TOP, BLUE_BOTTOM = "#1768E8", "#0A2A5C"
PIN_TOP, PIN_BOTTOM = "#FFC83A", "#FF7A00"
NAVY, GOLD = "#0A2A5C", "#FFD54A"

# ---- Geometrie im 108x108-Raster (sichtbarer Bereich 72x72 in der Mitte, sichere Zone 66x66)
PIN = "M54,83 C44,72 34,60 34,45 A20,20 0 0 1 74,45 C74,60 64,72 54,83 Z"
CX, CY, R_IN = 54, 45, 12.2
ROAD = "M-6,100 C26,86 22,66 54,64 S104,46 116,22"


def star(cx, cy, ro, ri, n=5, rot=-90):
    pts = []
    for i in range(n * 2):
        r = ro if i % 2 == 0 else ri
        a = math.radians(rot + i * 180 / n)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in pts) + " Z"


STAR = star(CX, CY + 0.4, 8.3, 3.7)
CIRCLE = f"M{CX - R_IN},{CY} a{R_IN},{R_IN} 0 1 0 {2 * R_IN},0 a{R_IN},{R_IN} 0 1 0 {-2 * R_IN},0 Z"

DEFS = f"""
<defs>
  <linearGradient id="bg" x1="0" y1="0" x2="108" y2="108" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="{BLUE_TOP}"/><stop offset="1" stop-color="{BLUE_BOTTOM}"/></linearGradient>
  <radialGradient id="glow" cx="30" cy="24" r="62" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#FFFFFF" stop-opacity="0.22"/><stop offset="1" stop-color="#FFFFFF" stop-opacity="0"/></radialGradient>
  <linearGradient id="pin" x1="54" y1="25" x2="54" y2="83" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="{PIN_TOP}"/><stop offset="1" stop-color="{PIN_BOTTOM}"/></linearGradient>
</defs>"""

BACKGROUND = f"""
<rect x="-10" y="-10" width="128" height="128" fill="url(#bg)"/>
<rect x="-10" y="-10" width="128" height="128" fill="url(#glow)"/>
<path d="{ROAD}" fill="none" stroke="#FFFFFF" stroke-opacity="0.15" stroke-width="13" stroke-linecap="round"/>
<path d="{ROAD}" fill="none" stroke="#FFFFFF" stroke-opacity="0.38" stroke-width="1.3" stroke-dasharray="3.2 3.6" stroke-linecap="round"/>
<path d="M29,30 l1.2,3.2 3.2,1.2 -3.2,1.2 -1.2,3.2 -1.2,-3.2 -3.2,-1.2 3.2,-1.2z" fill="#FFFFFF" fill-opacity="0.6"/>
<path d="M80,36 l0.9,2.4 2.4,0.9 -2.4,0.9 -0.9,2.4 -0.9,-2.4 -2.4,-0.9 2.4,-0.9z" fill="#FFFFFF" fill-opacity="0.45"/>
"""

FOREGROUND = f"""
<ellipse cx="54" cy="84.6" rx="12" ry="2.7" fill="#000000" fill-opacity="0.24"/>
<path d="{PIN}" fill="url(#pin)"/>
<path d="M40.6,36.5 A15.4,15.4 0 0 1 51,29.4" fill="none" stroke="#FFFFFF" stroke-opacity="0.55" stroke-width="1.7" stroke-linecap="round"/>
<circle cx="{CX}" cy="{CY}" r="{R_IN}" fill="{NAVY}"/>
<path d="{STAR}" fill="{GOLD}" stroke="{GOLD}" stroke-width="1.5" stroke-linejoin="round"/>
"""

MONOCHROME = f"""
<path d="{PIN} {CIRCLE}" fill="#000000" fill-rule="evenodd"/>
<path d="{STAR}" fill="#000000" stroke="#000000" stroke-width="1.5" stroke-linejoin="round"/>
"""


def svg(body, view="0 0 108 108", size=1024, clip=None):
    """clip: None (Vollbild), 'round' oder 'rounded'; das Bild zeigt den sichtbaren Bereich (view)."""
    x, y, w, h = [float(v) for v in view.split()]
    mask = ""
    cp = ""
    if clip == "round":
        cp = f'<clipPath id="m"><circle cx="{x + w / 2}" cy="{y + h / 2}" r="{w / 2}"/></clipPath>'
        mask = ' clip-path="url(#m)"'
    elif clip == "rounded":
        cp = f'<clipPath id="m"><rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{w * 0.225}"/></clipPath>'
        mask = ' clip-path="url(#m)"'
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="{view}">{DEFS}{cp}'
            f'<g{mask}>{body}</g></svg>')


FULL = BACKGROUND + FOREGROUND
VISIBLE = "18 18 72 72"  # der Bereich, den Launcher bei adaptiven Icons zeigen (72 von 108 dp)


def png(body, path, size, clip=None, view=VISIBLE):
    path.parent.mkdir(parents=True, exist_ok=True)
    cairosvg.svg2png(bytestring=svg(body, view, size, clip).encode(), write_to=str(path), output_width=size, output_height=size)


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def vd(inner, extra_ns=True):
    ns = ' xmlns:aapt="http://schemas.android.com/aapt"' if extra_ns else ""
    return (f'<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"{ns}\n'
            f'    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n{inner}</vector>\n')


def grad_linear(x1, y1, x2, y2, c1, c2):
    return (f'        <aapt:attr name="android:fillColor">\n            <gradient android:type="linear" android:startX="{x1}" android:startY="{y1}" android:endX="{x2}" android:endY="{y2}">\n'
            f'                <item android:offset="0" android:color="{c1}"/>\n                <item android:offset="1" android:color="{c2}"/>\n            </gradient>\n        </aapt:attr>\n')


def a(c):  # "#RRGGBB" -> "#FFRRGGBB"
    return "#FF" + c[1:].upper()


def build_android():
    bg = vd(
        '    <path android:pathData="M0,0h108v108h-108z">\n' + grad_linear(0, 0, 108, 108, a(BLUE_TOP), a(BLUE_BOTTOM)) + '    </path>\n'
        '    <path android:pathData="M0,0h108v108h-108z">\n'
        '        <aapt:attr name="android:fillColor">\n            <gradient android:type="radial" android:centerX="30" android:centerY="24" android:gradientRadius="62">\n'
        '                <item android:offset="0" android:color="#38FFFFFF"/>\n                <item android:offset="1" android:color="#00FFFFFF"/>\n            </gradient>\n        </aapt:attr>\n    </path>\n'
        f'    <path android:pathData="{ROAD}" android:strokeColor="#26FFFFFF" android:strokeWidth="13" android:strokeLineCap="round"/>\n'
        f'    <path android:pathData="{ROAD}" android:strokeColor="#61FFFFFF" android:strokeWidth="1.3" android:strokeLineCap="round"/>\n'
        '    <path android:pathData="M29,30 l1.2,3.2 3.2,1.2 -3.2,1.2 -1.2,3.2 -1.2,-3.2 -3.2,-1.2 3.2,-1.2z" android:fillColor="#99FFFFFF"/>\n'
        '    <path android:pathData="M80,36 l0.9,2.4 2.4,0.9 -2.4,0.9 -0.9,2.4 -0.9,-2.4 -2.4,-0.9 2.4,-0.9z" android:fillColor="#73FFFFFF"/>\n')
    fg = vd(
        '    <path android:pathData="M42,84.6 a12,2.7 0 1 0 24,0 a12,2.7 0 1 0 -24,0 z" android:fillColor="#3D000000"/>\n'
        f'    <path android:pathData="{PIN}">\n' + grad_linear(54, 25, 54, 83, a(PIN_TOP), a(PIN_BOTTOM)) + '    </path>\n'
        '    <path android:pathData="M40.6,36.5 A15.4,15.4 0 0 1 51,29.4" android:strokeColor="#8CFFFFFF" android:strokeWidth="1.7" android:strokeLineCap="round"/>\n'
        f'    <path android:pathData="{CIRCLE}" android:fillColor="{a(NAVY)}"/>\n'
        f'    <path android:pathData="{STAR}" android:fillColor="{a(GOLD)}" android:strokeColor="{a(GOLD)}" android:strokeWidth="1.5" android:strokeLineJoin="round"/>\n')
    mono = vd(
        f'    <path android:pathData="{PIN} {CIRCLE}" android:fillColor="#FF000000" android:fillType="evenOdd"/>\n'
        f'    <path android:pathData="{STAR}" android:fillColor="#FF000000" android:strokeColor="#FF000000" android:strokeWidth="1.5" android:strokeLineJoin="round"/>\n', extra_ns=False)
    write(RES / "drawable/ic_launcher_background.xml", bg)
    write(RES / "drawable/ic_launcher_foreground.xml", fg)
    write(RES / "drawable/ic_launcher_monochrome.xml", mono)
    adaptive = ('<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                '    <background android:drawable="@drawable/ic_launcher_background"/>\n    <foreground android:drawable="@drawable/ic_launcher_foreground"/>\n'
                '    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>\n</adaptive-icon>\n')
    write(RES / "mipmap-anydpi-v26/ic_launcher.xml", adaptive)
    write(RES / "mipmap-anydpi-v26/ic_launcher_round.xml", adaptive)
    # Mipmap-PNG fuer aeltere Systeme, Verknuepfungen und Werkzeuge: quadratisch (abgerundet) und rund
    for d, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        png(FULL, RES / f"mipmap-{d}/ic_launcher.png", px, "rounded")
        png(FULL, RES / f"mipmap-{d}/ic_launcher_round.png", px, "round")


def build_design():
    write(OUT / "kiloquest-icon.svg", svg(FULL, VISIBLE, 1024))
    write(OUT / "kiloquest-icon-rounded.svg", svg(FULL, VISIBLE, 1024, "rounded"))
    write(OUT / "kiloquest-icon-round.svg", svg(FULL, VISIBLE, 1024, "round"))
    write(OUT / "kiloquest-icon-background.svg", svg(BACKGROUND, "0 0 108 108", 1024))
    write(OUT / "kiloquest-icon-foreground.svg", svg(FOREGROUND, "0 0 108 108", 1024))
    write(OUT / "kiloquest-icon-monochrome.svg", svg(MONOCHROME, "0 0 108 108", 1024))
    sizes = [16, 32, 48, 64, 72, 96, 128, 144, 192, 256, 384, 512, 1024]
    for s in sizes:
        png(FULL, OUT / f"png/square/kiloquest-{s}.png", s)
        png(FULL, OUT / f"png/rounded/kiloquest-{s}.png", s, "rounded")
        png(FULL, OUT / f"png/round/kiloquest-{s}.png", s, "round")
    # einfarbig (Vorschau fuer Themed Icons), 432 px = 108 dp bei xxxhdpi
    cairosvg.svg2png(bytestring=svg(MONOCHROME, "0 0 108 108", 432, None).encode(), write_to=str(OUT / "png/kiloquest-monochrome-432.png"), output_width=432, output_height=432)
    # WebP und ICO
    base = Image.open(OUT / "png/rounded/kiloquest-1024.png").convert("RGBA")
    base.resize((512, 512), Image.LANCZOS).save(OUT / "kiloquest-512.webp", quality=92, method=6)
    base.save(OUT / "kiloquest.ico", sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    # Play Store: 512x512, 32 Bit PNG, quadratisch ohne eigene Rundung (Google maskiert selbst)
    png(FULL, PLAY / "icon-512.png", 512)


if __name__ == "__main__":
    build_android()
    build_design()
    print("Icon erzeugt:", OUT, PLAY)
