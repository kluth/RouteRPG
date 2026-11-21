#!/usr/bin/env python3
"""Erzeugt Play-Store-Grafiken aus den Robolectric-Screenshots (app/build/layout-shots) und dem Maskottchen.

Voraussetzung: ./gradlew :app:testDebugUnitTest --tests app.kiloquest.LayoutShotsTest
Ausgabe: docs/release/store/ (Telefon-Screenshots 1080x1920, Feature-Grafik 1024x500 je Sprache).
Play verlangt Screenshots mit Seitenverhaeltnis hoechstens 2:1; die Rohbilder (1:2,16) werden deshalb auf 9:16 eingepasst.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
SHOTS = ROOT / "app/build/layout-shots"
OUT = ROOT / "docs/release/store"
FONT = ROOT / "app/src/main/res/font/nunito_variable.ttf"
FOX = ROOT / "app/src/main/res/drawable-nodpi/kilo_fox.png"
# Navigationsansichten mit Karte kann der Robolectric-Test nicht rendern: dafuer echte Handy-Screenshots verwenden (docs/release/PLAY_TEST.md)
PICKS = ["mini-yes-390x844", "mini-choice-390x844", "mini-done-390x844", "mini-more-390x844", "explorer-plates-390x844", "mini-calm-390x844"]


def phone(src: Path, dst: Path):
    im = Image.open(src).convert("RGB")
    w, h = 1080, 1920
    scale = min(w / im.width, h / im.height)
    r = im.resize((int(im.width * scale), int(im.height * scale)), Image.LANCZOS)
    canvas = Image.new("RGB", (w, h), r.getpixel((2, 2)))  # Randfarbe des Bildes als Hintergrund
    canvas.paste(r, ((w - r.width) // 2, (h - r.height) // 2))
    canvas.save(dst, optimize=True)


def feature(lang: str):
    w, h = 1024, 500
    img = Image.new("RGB", (w, h))
    px = img.load()
    for y in range(h):  # senkrechter Verlauf Orange -> Gelb
        t = y / (h - 1)
        c = (int(255 - 20 * t), int(159 + 54 * t), int(67 - 0 * t + 40 * t))
        for x in range(w):
            px[x, y] = c
    d = ImageDraw.Draw(img)
    def fit(text, size, weight, max_w):
        while True:
            f = ImageFont.truetype(str(FONT), size)
            f.set_variation_by_axes([weight])
            if d.textlength(text, font=f) <= max_w or size <= 20:
                return f
            size -= 2
    fox = Image.open(FOX).convert("RGBA")
    fox.thumbnail((380, 380))
    img.paste(fox, (60, (h - fox.height) // 2), fox)
    title = "KiloQuest"
    sub = {"de": "Familien-Navi mit Schatzsuche", "en": "Family navigation with a treasure hunt"}[lang]
    max_w = w - 470 - 40
    d.text((470, 150), title, font=fit(title, 110, 800, max_w), fill=(40, 25, 10))
    d.text((474, 300), sub, font=fit(sub, 44, 600, max_w), fill=(60, 40, 15))
    img.save(OUT / f"feature-{lang}.png", optimize=True)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for i, name in enumerate(PICKS, 1):
        src = SHOTS / f"{name}.png"
        if src.exists():
            phone(src, OUT / f"phone-{i:02d}-{name.split('-')[0]}.png")
        else:
            print("fehlt:", src)
    for lang in ("de", "en"):
        feature(lang)
    print("fertig:", sorted(p.name for p in OUT.iterdir()))


main()
