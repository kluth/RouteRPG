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

# ---- Farbvarianten (Name -> Palette). "default" ist das Standard-Icon; die anderen sind in den Einstellungen waehlbar (activity-alias).
PALETTES = {
    "default": dict(bg1="#1768E8", bg2="#0A2A5C", pin1="#FFC83A", pin2="#FF7A00", navy="#0A2A5C", star="#FFD54A", glow=0.22),
    "night":   dict(bg1="#17243D", bg2="#04080F", pin1="#FFD35C", pin2="#FF9500", navy="#101B30", star="#FFE27A", glow=0.10),
    "sunset":  dict(bg1="#FF7E5F", bg2="#5B1A8F", pin1="#FFFFFF", pin2="#FFE0B2", navy="#5B1A8F", star="#FFB300", glow=0.25),
    "forest":  dict(bg1="#2FA463", bg2="#073A2B", pin1="#FFC83A", pin2="#FF7A00", navy="#073A2B", star="#FFD54A", glow=0.20),
    "mono":    dict(bg1="#444B55", bg2="#101317", pin1="#FFFFFF", pin2="#C5CBD4", navy="#101317", star="#FFFFFF", glow=0.14),
}
VARIANTS = list(PALETTES)
BLUE_TOP, BLUE_BOTTOM = PALETTES["default"]["bg1"], PALETTES["default"]["bg2"]
PIN_TOP, PIN_BOTTOM = PALETTES["default"]["pin1"], PALETTES["default"]["pin2"]
NAVY, GOLD = PALETTES["default"]["navy"], PALETTES["default"]["star"]

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

def parts(pal):
    """(defs, hintergrund, vordergrund, einfarbig) als SVG-Text fuer eine Palette."""
    defs = f"""
<defs>
  <linearGradient id="bg" x1="0" y1="0" x2="108" y2="108" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="{pal['bg1']}"/><stop offset="1" stop-color="{pal['bg2']}"/></linearGradient>
  <radialGradient id="glow" cx="30" cy="24" r="62" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#FFFFFF" stop-opacity="{pal['glow']}"/><stop offset="1" stop-color="#FFFFFF" stop-opacity="0"/></radialGradient>
  <linearGradient id="pin" x1="54" y1="25" x2="54" y2="83" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="{pal['pin1']}"/><stop offset="1" stop-color="{pal['pin2']}"/></linearGradient>
</defs>"""
    bg = f"""
<rect x="-10" y="-10" width="128" height="128" fill="url(#bg)"/>
<rect x="-10" y="-10" width="128" height="128" fill="url(#glow)"/>
<path d="{ROAD}" fill="none" stroke="#FFFFFF" stroke-opacity="0.15" stroke-width="13" stroke-linecap="round"/>
<path d="{ROAD}" fill="none" stroke="#FFFFFF" stroke-opacity="0.38" stroke-width="1.3" stroke-dasharray="3.2 3.6" stroke-linecap="round"/>
<path d="M29,30 l1.2,3.2 3.2,1.2 -3.2,1.2 -1.2,3.2 -1.2,-3.2 -3.2,-1.2 3.2,-1.2z" fill="#FFFFFF" fill-opacity="0.6"/>
<path d="M80,36 l0.9,2.4 2.4,0.9 -2.4,0.9 -0.9,2.4 -0.9,-2.4 -2.4,-0.9 2.4,-0.9z" fill="#FFFFFF" fill-opacity="0.45"/>
"""
    fg = f"""
<ellipse cx="54" cy="84.6" rx="12" ry="2.7" fill="#000000" fill-opacity="0.24"/>
<path d="{PIN}" fill="url(#pin)"/>
<path d="M40.6,36.5 A15.4,15.4 0 0 1 51,29.4" fill="none" stroke="#FFFFFF" stroke-opacity="0.55" stroke-width="1.7" stroke-linecap="round"/>
<circle cx="{CX}" cy="{CY}" r="{R_IN}" fill="{pal['navy']}"/>
<path d="{STAR}" fill="{pal['star']}" stroke="{pal['star']}" stroke-width="1.5" stroke-linejoin="round"/>
"""
    mono = f"""
<path d="{PIN} {CIRCLE}" fill="#000000" fill-rule="evenodd"/>
<path d="{STAR}" fill="#000000" stroke="#000000" stroke-width="1.5" stroke-linejoin="round"/>
"""
    return defs, bg, fg, mono


DEFS, BACKGROUND, FOREGROUND, MONOCHROME = parts(PALETTES["default"])
FULL = BACKGROUND + FOREGROUND



# ---- Zeichen im Pin fuer dynamische Benachrichtigungs-Icons (Koordinaten im 108er-Raster, Mitte des Kreises bei 54,45)
def _rect(x1, y1, x2, y2):
    return f"M{x1},{y1} H{x2} V{y2} H{x1} Z"


def _bar(cx, cy, r0, r1, w, ang):
    """Balken von Radius r0 bis r1 (Breite w) in Richtung ang (Grad), als Polygon."""
    a_ = math.radians(ang)
    ux, uy = math.cos(a_), math.sin(a_)
    nx, ny = -uy * w / 2, ux * w / 2
    p = [(cx + ux * r0 + nx, cy + uy * r0 + ny), (cx + ux * r1 + nx, cy + uy * r1 + ny), (cx + ux * r1 - nx, cy + uy * r1 - ny), (cx + ux * r0 - nx, cy + uy * r0 - ny)]
    return "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in p) + " Z"


def _circle(cx, cy, r):
    return f"M{cx - r},{cy} a{r},{r} 0 1 0 {2 * r},0 a{r},{r} 0 1 0 {-2 * r},0 Z"


GLYPHS = {
    "star": STAR,
    "arrow": "M54,37.2 L60.2,52.2 L54,48.7 L47.8,52.2 Z",
    "pause": _rect(49.2, 39.2, 52.6, 50.8) + " " + _rect(55.4, 39.2, 58.8, 50.8),
    "wc": _circle(49.6, 39.8, 1.8) + " M47.7,42.4 H51.5 V48.8 H47.7 Z " + _circle(58.4, 39.8, 1.8) + " M58.4,42.4 L61.7,49.4 H55.1 Z",
    "camera": "M47.4,41.6 H50.6 L51.8,39.6 H56.2 L57.4,41.6 H60.6 V50.6 H47.4 Z " + _circle(54, 46, 3.1),
    "calendar": _rect(47.4, 39.2, 60.6, 51.6) + " " + _rect(49.1, 42.6, 58.9, 50) + " " + _rect(50.4, 37.6, 51.8, 40.6) + " " + _rect(56.2, 37.6, 57.6, 40.6)
                + " " + _rect(50.6, 44.2, 52.6, 46.2) + " " + _rect(53, 44.2, 55, 46.2) + " " + _rect(55.4, 44.2, 57.4, 46.2),
    "warning": "M54,37.4 L61.6,50.8 H46.4 Z " + _rect(53.2, 42.4, 54.8, 47.4) + " " + _circle(54, 49.1, 1.0),
    "cross": "M52.3,38.4 H55.7 V43.3 H60.6 V46.7 H55.7 V51.6 H52.3 V46.7 H47.4 V43.3 H52.3 Z",
    "book": "M53.4,40.6 C51.2,39.2 48.9,38.9 46.7,39.5 V50.7 C48.9,50.1 51.2,50.4 53.4,51.8 Z M54.6,40.6 C56.8,39.2 59.1,38.9 61.3,39.5 V50.7 C59.1,50.1 56.8,50.4 54.6,51.8 Z",
    "flag": _rect(48.6, 37.8, 50.2, 52.4) + " M50.2,38.4 H60.4 L57.2,42.7 L60.4,47 H50.2 Z",
    "moon": "M57,37.6 A8.2,8.2 0 1 0 57,52.4 A6.6,6.6 0 1 1 57,37.6 Z",
    "snow": " ".join(_bar(54, 45.2, -7.6, 7.6, 1.9, ang) for ang in (0, 60, 120)),
    "sun": _circle(54, 45.2, 3.3) + " " + " ".join(_bar(54, 45.2, 5.4, 8.3, 1.8, k * 45) for k in range(8)),
}

ALERT_PAL = dict(bg1="#E5483F", bg2="#7A0F12", pin1="#FFFFFF", pin2="#FFE3E0", navy="#7A0F12", star="#FFD54A", glow=0.22)
NOTIF_PALETTES = {"DAY": PALETTES["default"], "DUSK": PALETTES["sunset"], "NIGHT": PALETTES["night"], "FOREST": PALETTES["forest"], "MONO": PALETTES["mono"], "ALERT": ALERT_PAL}

VISIBLE = "18 18 72 72"  # der Bereich, den Launcher bei adaptiven Icons zeigen (72 von 108 dp)


# ---- Masken (Formen) fuer Vorschau und PNG-Varianten; Koordinaten im sichtbaren Bereich (x, y, w, h)
def superellipse(x, y, w, h, n=4.0, steps=96):
    pts = []
    for k in range(steps):
        t = 2 * math.pi * k / steps
        c, s_ = math.cos(t), math.sin(t)
        px = x + w / 2 + (w / 2) * math.copysign(abs(c) ** (2 / n), c)
        py = y + h / 2 + (h / 2) * math.copysign(abs(s_) ** (2 / n), s_)
        pts.append(f"{px:.2f},{py:.2f}")
    return "M" + " L".join(pts) + " Z"


def polygon_rounded(cx, cy, r, n, rot=-90):
    pts = [(cx + r * math.cos(math.radians(rot + i * 360 / n)), cy + r * math.sin(math.radians(rot + i * 360 / n))) for i in range(n)]
    return "M" + " L".join(f"{px:.2f},{py:.2f}" for px, py in pts) + " Z"


def scallop(cx, cy, r, bumps=12, depth=0.06, steps=240):
    pts = []
    for k in range(steps):
        t = 2 * math.pi * k / steps
        rr = r * (1 - depth + depth * math.cos(bumps * t))
        pts.append(f"{cx + rr * math.cos(t):.2f},{cy + rr * math.sin(t):.2f}")
    return "M" + " L".join(pts) + " Z"


def teardrop(x, y, w, h, big=0.5, small=0.08):
    rb, rs = w * big, w * small
    return (f"M{x + rb},{y} H{x + w - rb} A{rb},{rb} 0 0 1 {x + w},{y + rb} V{y + h - rb} A{rb},{rb} 0 0 1 {x + w - rb},{y + h} "
            f"H{x + rs} A{rs},{rs} 0 0 1 {x},{y + h - rs} V{y + rb} A{rb},{rb} 0 0 1 {x + rb},{y} Z")


def shape_path(name, view=VISIBLE):
    x, y, w, h = [float(v) for v in view.split()]
    cx, cy = x + w / 2, y + h / 2
    if name == "circle":
        return f"M{cx - w / 2},{cy} a{w / 2},{h / 2} 0 1 0 {w},0 a{w / 2},{h / 2} 0 1 0 {-w},0 Z"
    if name == "squircle":
        return superellipse(x, y, w, h, 4.0)
    if name == "rounded":
        r = w * 0.225
        return f"M{x + r},{y} H{x + w - r} A{r},{r} 0 0 1 {x + w},{y + r} V{y + h - r} A{r},{r} 0 0 1 {x + w - r},{y + h} H{x + r} A{r},{r} 0 0 1 {x},{y + h - r} V{y + r} A{r},{r} 0 0 1 {x + r},{y} Z"
    if name == "soft-square":
        r = w * 0.10
        return f"M{x + r},{y} H{x + w - r} A{r},{r} 0 0 1 {x + w},{y + r} V{y + h - r} A{r},{r} 0 0 1 {x + w - r},{y + h} H{x + r} A{r},{r} 0 0 1 {x},{y + h - r} V{y + r} A{r},{r} 0 0 1 {x + r},{y} Z"
    if name == "teardrop":
        return teardrop(x, y, w, h)
    if name == "pill":  # senkrechte Kapsel, Inhalt bleibt mittig sichtbar
        pw = w * 0.80
        r = pw / 2
        px = x + (w - pw) / 2
        return f"M{px + r},{y} A{r},{r} 0 0 1 {px + pw},{y + r} V{y + h - r} A{r},{r} 0 0 1 {px + r},{y + h} A{r},{r} 0 0 1 {px},{y + h - r} V{y + r} A{r},{r} 0 0 1 {px + r},{y} Z"
    if name == "hexagon":
        return polygon_rounded(cx, cy, w / 2 * 1.0, 6, rot=-90)
    if name == "cookie":
        return scallop(cx, cy, w / 2, 12, 0.055)
    if name == "square":
        return f"M{x},{y} H{x + w} V{y + h} H{x} Z"
    raise ValueError(name)


SHAPES = ["square", "soft-square", "rounded", "squircle", "circle", "teardrop", "pill", "hexagon", "cookie"]


def svg(body, view="0 0 108 108", size=1024, shape=None, defs=None):
    """shape: None (Vollbild) oder ein Name aus SHAPES; das Bild zeigt den sichtbaren Bereich (view)."""
    mask = cp = ""
    if shape and shape != "square":
        cp = f'<clipPath id="m"><path d="{shape_path(shape, view)}"/></clipPath>'
        mask = ' clip-path="url(#m)"'
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="{view}">{defs or DEFS}{cp}'
            f'<g{mask}>{body}</g></svg>')


def png(body, path, size, shape=None, view=VISIBLE, defs=None):
    path.parent.mkdir(parents=True, exist_ok=True)
    cairosvg.svg2png(bytestring=svg(body, view, size, shape, defs).encode(), write_to=str(path), output_width=size, output_height=size)


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


def glow_hex(opacity):
    return "#%02XFFFFFF" % round(opacity * 255)


def android_layers(pal):
    bg = vd(
        '    <path android:pathData="M0,0h108v108h-108z">\n' + grad_linear(0, 0, 108, 108, a(pal["bg1"]), a(pal["bg2"])) + '    </path>\n'
        '    <path android:pathData="M0,0h108v108h-108z">\n'
        '        <aapt:attr name="android:fillColor">\n            <gradient android:type="radial" android:centerX="30" android:centerY="24" android:gradientRadius="62">\n'
        f'                <item android:offset="0" android:color="{glow_hex(pal["glow"])}"/>\n                <item android:offset="1" android:color="#00FFFFFF"/>\n            </gradient>\n        </aapt:attr>\n    </path>\n'
        f'    <path android:pathData="{ROAD}" android:strokeColor="#26FFFFFF" android:strokeWidth="13" android:strokeLineCap="round"/>\n'
        f'    <path android:pathData="{ROAD}" android:strokeColor="#61FFFFFF" android:strokeWidth="1.3" android:strokeLineCap="round"/>\n'
        '    <path android:pathData="M29,30 l1.2,3.2 3.2,1.2 -3.2,1.2 -1.2,3.2 -1.2,-3.2 -3.2,-1.2 3.2,-1.2z" android:fillColor="#99FFFFFF"/>\n'
        '    <path android:pathData="M80,36 l0.9,2.4 2.4,0.9 -2.4,0.9 -0.9,2.4 -0.9,-2.4 -2.4,-0.9 2.4,-0.9z" android:fillColor="#73FFFFFF"/>\n')
    fg = vd(
        '    <path android:pathData="M42,84.6 a12,2.7 0 1 0 24,0 a12,2.7 0 1 0 -24,0 z" android:fillColor="#3D000000"/>\n'
        f'    <path android:pathData="{PIN}">\n' + grad_linear(54, 25, 54, 83, a(pal["pin1"]), a(pal["pin2"])) + '    </path>\n'
        '    <path android:pathData="M40.6,36.5 A15.4,15.4 0 0 1 51,29.4" android:strokeColor="#8CFFFFFF" android:strokeWidth="1.7" android:strokeLineCap="round"/>\n'
        f'    <path android:pathData="{CIRCLE}" android:fillColor="{a(pal["navy"])}"/>\n'
        f'    <path android:pathData="{STAR}" android:fillColor="{a(pal["star"])}" android:strokeColor="{a(pal["star"])}" android:strokeWidth="1.5" android:strokeLineJoin="round"/>\n')
    return bg, fg


def build_android():
    mono = vd(
        f'    <path android:pathData="{PIN} {CIRCLE}" android:fillColor="#FF000000" android:fillType="evenOdd"/>\n'
        f'    <path android:pathData="{STAR}" android:fillColor="#FF000000" android:strokeColor="#FF000000" android:strokeWidth="1.5" android:strokeLineJoin="round"/>\n', extra_ns=False)
    write(RES / "drawable/ic_launcher_monochrome.xml", mono)
    for v, pal in PALETTES.items():
        suffix = "" if v == "default" else f"_{v}"
        bg, fg = android_layers(pal)
        write(RES / f"drawable/ic_launcher{suffix}_background.xml", bg)
        write(RES / f"drawable/ic_launcher{suffix}_foreground.xml", fg)
        adaptive = ('<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                    f'    <background android:drawable="@drawable/ic_launcher{suffix}_background"/>\n    <foreground android:drawable="@drawable/ic_launcher{suffix}_foreground"/>\n'
                    '    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>\n</adaptive-icon>\n')
        write(RES / f"mipmap-anydpi-v26/ic_launcher{suffix}.xml", adaptive)
        if v == "default":
            write(RES / "mipmap-anydpi-v26/ic_launcher_round.xml", adaptive)
        # Vorschau fuer die Auswahl in den Einstellungen (rund, 192 px)
        defs, bgs, fgs, _ = parts(pal)
        png(bgs + fgs, RES / f"drawable-nodpi/icon_preview_{v}.png", 192, "squircle", defs=defs)
    # Mipmap-PNG fuer aeltere Systeme, Verknuepfungen und Werkzeuge: quadratisch (abgerundet) und rund
    for d, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        png(FULL, RES / f"mipmap-{d}/ic_launcher.png", px, "rounded")
        png(FULL, RES / f"mipmap-{d}/ic_launcher_round.png", px, "circle")
    # Benachrichtigungs-Symbole (einfarbig, 24 dp): Pin mit ausgeschnittenem Kreis und einem Zeichen darin, je Anlass ein anderes
    for gid, gpath in GLYPHS.items():
        write(RES / f"drawable/ic_stat_{gid}.xml",
              '<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
              '    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">\n'
              '    <group android:scaleX="0.4" android:scaleY="0.4" android:translateX="-9.6" android:translateY="-9.6">\n'
              f'        <path android:pathData="{PIN} {CIRCLE}" android:fillColor="#FFFFFFFF" android:fillType="evenOdd"/>\n'
              f'        <path android:pathData="{gpath}" android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:strokeColor="#FFFFFFFF" android:strokeWidth="0.5" android:strokeLineJoin="round"/>\n'
              '    </group>\n</vector>\n')
    # Gemeinsame Geometrie fuer das grosse Icon, das die App zur Laufzeit zeichnet (erzeugt, nicht von Hand aendern)
    def kc(c): return "0xFF" + c[1:].upper() + ".toInt()"
    pals = "\n".join(f'        "{n}" to Pal({kc(p["bg1"])}, {kc(p["bg2"])}, {kc(p["pin1"])}, {kc(p["pin2"])}, {kc(p["navy"])}, {kc(p["star"])}, {p["glow"]}f),' for n, p in NOTIF_PALETTES.items())
    gl = "\n".join(f'        "{gid}" to "{gpath}",' for gid, gpath in GLYPHS.items())
    write(ROOT / "app/src/main/java/app/kiloquest/IconGeometry.kt",
          "package app.kiloquest\n\n// ERZEUGT von tools/icon/build_icon.py, nicht von Hand aendern.\n\n"
          "/** Geometrie (108er-Raster) und Farben des App-Icons, genutzt fuer das dynamische grosse Benachrichtigungs-Icon. */\n"
          "object IconGeometry {\n"
          f'    const val PIN = "{PIN}"\n    const val CIRCLE_CX = {CX}f\n    const val CIRCLE_CY = {CY}f\n    const val CIRCLE_R = {R_IN}f\n    const val ROAD = "{ROAD}"\n\n'
          "    class Pal(val bg1: Int, val bg2: Int, val pin1: Int, val pin2: Int, val navy: Int, val glyph: Int, val glow: Float)\n\n"
          f"    val PALETTES: Map<String, Pal> = mapOf(\n{pals}\n    )\n\n"
          f"    val GLYPHS: Map<String, String> = mapOf(\n{gl}\n    )\n}}\n")
    # Start-Bildschirm (Android 12+): Marken-Hintergrund und Vordergrund-Ebene
    write(RES / "values/themes.xml",
          '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <style name="Theme.KiloQuest" parent="@android:style/Theme.Material.Light.NoActionBar" />\n</resources>\n')
    write(RES / "values-v31/themes.xml",
          '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <style name="Theme.KiloQuest" parent="@android:style/Theme.Material.Light.NoActionBar">\n'
          f'        <item name="android:windowSplashScreenBackground">{PALETTES["default"]["bg2"]}</item>\n'
          '        <item name="android:windowSplashScreenAnimatedIcon">@drawable/ic_launcher_foreground</item>\n'
          '    </style>\n</resources>\n')


def build_design():
    # Quellen (Standard-Palette)
    write(OUT / "kiloquest-icon.svg", svg(FULL, VISIBLE, 1024))
    write(OUT / "kiloquest-icon-background.svg", svg(BACKGROUND, "0 0 108 108", 1024))
    write(OUT / "kiloquest-icon-foreground.svg", svg(FOREGROUND, "0 0 108 108", 1024))
    write(OUT / "kiloquest-icon-monochrome.svg", svg(MONOCHROME, "0 0 108 108", 1024))
    for sh in SHAPES:
        write(OUT / f"svg/kiloquest-{sh}.svg", svg(FULL, VISIBLE, 1024, sh))
    # PNG je Form und Groesse (Standard-Palette)
    sizes = [16, 32, 48, 64, 72, 96, 128, 144, 192, 256, 384, 512, 1024]
    for sh in SHAPES:
        # die gaengigen Formen in allen Groessen, die uebrigen in den wichtigsten
        for px in (sizes if sh in ("square", "rounded", "circle") else (48, 96, 192, 512, 1024)):
            png(FULL, OUT / f"png/{sh}/kiloquest-{px}.png", px, sh)
    # Farbvarianten (Themes): quadratisch/abgerundet/rund in wenigen Groessen + SVG
    for v, pal in PALETTES.items():
        defs, bgs, fgs, mono = parts(pal)
        write(OUT / f"themes/{v}/kiloquest-{v}.svg", svg(bgs + fgs, VISIBLE, 1024, None, defs))
        for sh in ("square", "rounded", "circle"):
            for px in (48, 96, 192, 512, 1024):
                png(bgs + fgs, OUT / f"themes/{v}/{sh}/kiloquest-{v}-{px}.png", px, sh, defs=defs)
    # einfarbige Ebene: schwarz auf transparent, weiss auf transparent (fuer dunkle Flaechen)
    cairosvg.svg2png(bytestring=svg(MONOCHROME, "0 0 108 108", 432).encode(), write_to=str(OUT / "png/monochrome/kiloquest-monochrome-432.png"), output_width=432, output_height=432) if (OUT / "png/monochrome").mkdir(parents=True, exist_ok=True) is None else None
    white = svg(MONOCHROME.replace("#000000", "#FFFFFF"), "0 0 108 108", 432)
    cairosvg.svg2png(bytestring=white.encode(), write_to=str(OUT / "png/monochrome/kiloquest-monochrome-white-432.png"), output_width=432, output_height=432)
    # SVG-Favicon mit Hell-/Dunkelmodus (prefers-color-scheme)
    ld, lbg, lfg, _ = parts(PALETTES["default"])
    nd, nbg, nfg, _ = parts(PALETTES["night"])
    nd_ids = nd.replace('id="bg"', 'id="bgN"').replace('id="glow"', 'id="glowN"').replace('id="pin"', 'id="pinN"')
    nbg = nbg.replace("url(#bg)", "url(#bgN)").replace("url(#glow)", "url(#glowN)")
    nfg = nfg.replace("url(#pin)", "url(#pinN)")
    favicon = ('<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="18 18 72 72">'
               '<style>.d{display:none}@media (prefers-color-scheme: dark){.l{display:none}.d{display:inline}}</style>'
               f'{ld}{nd_ids}<clipPath id="m"><path d="{shape_path("rounded")}"/></clipPath>'
               f'<g class="l" clip-path="url(#m)">{lbg}{lfg}</g><g class="d" clip-path="url(#m)">{nbg}{nfg}</g></svg>')
    write(OUT / "favicon.svg", favicon)
    base = Image.open(OUT / "png/rounded/kiloquest-1024.png").convert("RGBA")
    base.resize((512, 512), Image.LANCZOS).save(OUT / "kiloquest-512.webp", quality=92, method=6)
    base.save(OUT / "kiloquest.ico", sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    for px in (180, 192, 512):  # Web-App-Manifest / Apple-Touch (Vollbild, Betriebssystem rundet selbst)
        png(FULL, OUT / f"web/icon-{px}.png", px)
    # Play Store: 512x512, 32 Bit PNG, quadratisch ohne eigene Rundung (Google maskiert selbst)
    png(FULL, PLAY / "icon-512.png", 512)
    build_previews()


def large_svg(gid, pal, size=192):
    defs, bgs, _, _ = parts(pal)
    body = bgs + f"""
<ellipse cx="54" cy="84.6" rx="12" ry="2.7" fill="#000000" fill-opacity="0.24"/>
<path d="{PIN}" fill="url(#pin)"/>
<path d="M40.6,36.5 A15.4,15.4 0 0 1 51,29.4" fill="none" stroke="#FFFFFF" stroke-opacity="0.55" stroke-width="1.7" stroke-linecap="round"/>
<circle cx="{CX}" cy="{CY}" r="{R_IN}" fill="{pal['navy']}"/>
<path d="{GLYPHS[gid]}" fill="{pal['star']}" fill-rule="evenodd" stroke="{pal['star']}" stroke-width="0.8" stroke-linejoin="round"/>"""
    return svg(body, VISIBLE, size, "circle", defs)


def small_svg(gid, size=96, color="#FFFFFF"):
    body = (f'<path d="{PIN} {CIRCLE}" fill="{color}" fill-rule="evenodd"/>'
            f'<path d="{GLYPHS[gid]}" fill="{color}" fill-rule="evenodd" stroke="{color}" stroke-width="0.5" stroke-linejoin="round"/>')
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="30 21 48 66">{body}</svg>'


def build_notification_previews():
    scenarios = [  # (Beschriftung ohne Text; nur Bild) Zeichen, Palette
        ("arrow", "DAY"), ("arrow", "FOREST"), ("snow", "FOREST"), ("arrow", "DUSK"), ("moon", "NIGHT"), ("star", "DAY"),
        ("pause", "DAY"), ("wc", "DAY"), ("camera", "DUSK"), ("calendar", "DAY"), ("flag", "FOREST"), ("book", "NIGHT"), ("warning", "ALERT"), ("cross", "ALERT"),
    ]
    cell, pad = 120, 16
    w = len(scenarios) * (cell + pad) + pad
    sheet = Image.new("RGBA", (w, 2 * cell + 4 * pad), (232, 234, 238, 255))
    for i, (g, pn) in enumerate(scenarios):
        big = OUT / "png/notification/_tmp.png"
        big.parent.mkdir(parents=True, exist_ok=True)
        cairosvg.svg2png(bytestring=large_svg(g, NOTIF_PALETTES[pn], cell).encode(), write_to=str(big), output_width=cell, output_height=cell)
        sheet.alpha_composite(Image.open(big).convert("RGBA"), (pad + i * (cell + pad), pad))
        # kleines Symbol: weiss auf dunkler Statusleiste
        bar = Image.new("RGBA", (cell, cell), (36, 40, 46, 255))
        cairosvg.svg2png(bytestring=small_svg(g, cell // 2).encode(), write_to=str(big), output_width=int(cell * 0.45), output_height=int(cell * 0.62))
        sm = Image.open(big).convert("RGBA")
        bar.alpha_composite(sm, ((cell - sm.width) // 2, (cell - sm.height) // 2))
        sheet.alpha_composite(bar, (pad + i * (cell + pad), 2 * pad + cell))
    (OUT / "png/notification/_tmp.png").unlink()
    sheet.save(OUT / "preview-notifications.png")
    # einzelne Symbole als SVG
    for gid in GLYPHS:
        write(OUT / f"notification/small-{gid}.svg", small_svg(gid, 96, "#000000"))
        for pn, pal in NOTIF_PALETTES.items():
            write(OUT / f"notification/large-{gid}-{pn.lower()}.svg", large_svg(gid, pal, 256))


def build_previews():
    """Uebersichtsbilder: alle Formen, alle Farbvarianten, Themed-Icon-Beispiele (Hell/Dunkel)."""
    cell, pad = 160, 20
    sheet = Image.new("RGBA", (len(SHAPES) * (cell + pad) + pad, cell + 2 * pad), (236, 238, 242, 255))
    for i, sh in enumerate(SHAPES):
        im = Image.open(OUT / f"png/{sh}/kiloquest-192.png").convert("RGBA").resize((cell, cell), Image.LANCZOS)
        sheet.alpha_composite(im, (pad + i * (cell + pad), pad))
    sheet.save(OUT / "preview-shapes.png")

    sheet = Image.new("RGBA", (len(PALETTES) * (cell + pad) + pad, cell + 2 * pad), (236, 238, 242, 255))
    for i, v in enumerate(PALETTES):
        im = Image.open(OUT / f"themes/{v}/rounded/kiloquest-{v}-192.png").convert("RGBA").resize((cell, cell), Image.LANCZOS)
        sheet.alpha_composite(im, (pad + i * (cell + pad), pad))
    sheet.save(OUT / "preview-colors.png")

    # Themed Icons (Android 13+): das System faerbt die einfarbige Ebene mit Hintergrund-/Vordergrundfarbe der Wallpaper-Palette
    tints = [  # (Hintergrund, Symbol, Seitenhintergrund)
        ("#D6E3FF", "#001B3E", "#F4F6FA"), ("#C8F0D2", "#00210E", "#F4F8F5"), ("#FFDCC8", "#331200", "#FAF5F2"), ("#EBD8FF", "#25005A", "#F7F4FA"),
        ("#2B4678", "#D6E3FF", "#12161C"), ("#265C3A", "#C8F0D2", "#121714"), ("#7A3B1C", "#FFDCC8", "#1A1512"), ("#563B80", "#EBD8FF", "#16121B"),
    ]
    cw = 4 * (cell + pad) + pad
    sheet = Image.new("RGBA", (cw, 2 * (cell + pad) + pad), (255, 255, 255, 255))
    mono_alpha = Image.open(OUT / "png/monochrome/kiloquest-monochrome-432.png").convert("RGBA").split()[3]
    for i, (bgc, fgc, page) in enumerate(tints):
        col, row = i % 4, i // 4
        x, y = pad + col * (cell + pad), pad + row * (cell + pad)
        tile = Image.new("RGBA", (cell, cell), page)
        circle = Image.new("L", (cell * 4, cell * 4), 0)
        from PIL import ImageDraw
        ImageDraw.Draw(circle).ellipse((0, 0, cell * 4 - 1, cell * 4 - 1), fill=255)
        bgimg = Image.new("RGBA", (cell, cell), bgc)
        tile.paste(bgimg, (0, 0), circle.resize((cell, cell), Image.LANCZOS))
        fg = Image.new("RGBA", (cell, cell), fgc)
        # sichtbarer Bereich 72/108: einfarbige Ebene auf 108/72-fache Groesse skalieren und mittig zuschneiden
        big = mono_alpha.resize((int(cell * 1.5), int(cell * 1.5)), Image.LANCZOS)
        off = (big.width - cell) // 2
        mask = big.crop((off, off, off + cell, off + cell))
        tile.paste(fg, (0, 0), mask)
        sheet.alpha_composite(tile, (x, y))
    sheet.save(OUT / "preview-themed.png")
    build_notification_previews()


if __name__ == "__main__":
    build_android()
    build_design()
    print("Icon erzeugt:", OUT, PLAY)
