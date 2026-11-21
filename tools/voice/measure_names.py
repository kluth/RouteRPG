#!/usr/bin/env python3
"""Misst, wie gut Namenlisten Strassennamen abdecken (Spec 016, Schritt 1).

Eingabe (eine der Varianten):
  --overpass-json DATEI...   Overpass-Antworten ([out:json]; way[highway][name]; out tags;), z. B. je Region eine Datei
  --fetch                    laedt die Beispielregionen selbst von einer Overpass-Instanz (Netz noetig; Rate beachten, --pause)
  --selftest                 prueft das Skript mit eingebauter Mini-Stichprobe

Ausgabe: Zahl der Wege und verschiedenen Namen, die Abdeckungskurve (Anteil der Wege, den die haeufigsten N Namen abdecken),
haeufigste Namen, haeufigste Stamm-Woerter und Suffixe. Die Zahlen gelten nur fuer die gelesenen Regionen (Stichprobe!).
Daten: OpenStreetMap-Mitwirkende, ODbL; die Rohdaten werden hier nicht gespeichert.
"""
import argparse, collections, json, sys, time, urllib.parse, urllib.request

REGIONS = {  # Name: (sued, west, nord, ost) - Stadt, Kleinstadt, laendlich (grobe Boxen)
    "Hamburg-Mitte": (53.54, 9.95, 53.58, 10.05),
    "Hameln": (52.08, 9.33, 52.13, 9.42),
    "Eifel-Land": (50.30, 6.60, 50.42, 6.80),
    "Muenchen-Sued": (48.08, 11.50, 48.12, 11.60),
}
SUFFIXES = ["straße", "strasse", "weg", "allee", "platz", "gasse", "ring", "damm", "ufer", "pfad", "steig", "chaussee", "markt", "hof", "park"]
CUTS = [10, 50, 100, 500, 1000, 5000, 20000, 100000]


def fetch(box, endpoint, pause):
    s, w, n, e = box
    q = f"[out:json][timeout:90];way({s},{w},{n},{e})[highway][name];out tags;"
    req = urllib.request.Request(endpoint, data=urllib.parse.urlencode({"data": q}).encode(), headers={"User-Agent": "KiloQuest-measure/0.1 (private)"})
    with urllib.request.urlopen(req, timeout=120) as r:
        data = json.load(r)
    time.sleep(pause)
    return data


def names_of(data):
    return [el["tags"]["name"] for el in data.get("elements", []) if "name" in el.get("tags", {})]


def stem(name):
    low = name.lower()
    for suf in SUFFIXES:
        if low.endswith(suf) and len(low) > len(suf) + 1:
            return name[: len(name) - len(suf)], suf
    return None, None


def report(names):
    c = collections.Counter(names)
    total = sum(c.values())
    ranked = [v for _, v in c.most_common()]
    print(f"Wege mit Namen: {total}, verschiedene Namen: {len(c)}")
    acc = 0
    cum = []
    for v in ranked:
        acc += v
        cum.append(acc)
    print("Abdeckung der Wege durch die haeufigsten N Namen:")
    for n in CUTS:
        if n <= len(c) or n == CUTS[0]:
            k = min(n, len(c))
            print(f"  Top {k:>6}: {100 * cum[k - 1] / total:5.1f} %")
    print("Haeufigste Namen:", ", ".join(f"{n} ({v})" for n, v in c.most_common(15)))
    stems, sufs = collections.Counter(), collections.Counter()
    for n, v in c.items():
        st, su = stem(n)
        if su:
            stems[st] += v
            sufs[su] += v
    print("Suffixe:", ", ".join(f"{k} {100 * v / total:.0f} %" for k, v in sufs.most_common(8)))
    print("Haeufigste Staemme:", ", ".join(f"{k} ({v})" for k, v in stems.most_common(15)))
    print(f"Verschiedene Staemme: {len(stems)} (gegenueber {len(c)} Namen)")
    return {"ways": total, "unique": len(c), "top": c.most_common(15)}


SELFTEST = ["Hauptstraße"] * 30 + ["Bahnhofstraße"] * 20 + ["Schulstraße"] * 10 + ["Gartenweg"] * 5 + [f"Seltene Straße {i}" for i in range(35)]


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--overpass-json", nargs="*", default=[])
    ap.add_argument("--fetch", action="store_true")
    ap.add_argument("--endpoint", default="https://overpass-api.de/api/interpreter")
    ap.add_argument("--pause", type=float, default=20.0)
    ap.add_argument("--selftest", action="store_true")
    a = ap.parse_args()
    names = []
    if a.selftest:
        r = report(SELFTEST)
        assert r["ways"] == 100 and r["unique"] == 39 and r["top"][0] == ("Hauptstraße", 30)
        print("Selbsttest ok")
        return
    for p in a.overpass_json:
        names += names_of(json.load(open(p, encoding="utf-8")))
    if a.fetch:
        for region, box in REGIONS.items():
            print(f"lade {region} ...", file=sys.stderr)
            names += names_of(fetch(box, a.endpoint, a.pause))
    if not names:
        sys.exit("keine Daten: --overpass-json, --fetch oder --selftest angeben")
    report(names)


main()
