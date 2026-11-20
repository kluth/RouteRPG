#!/usr/bin/env python3
"""Baut tools/voice/lines.json aus den Android-String-Ressourcen (Quest-Katalog + feste Kilo-Saetze).

Jede Zeile hat eine stabile Id (= Dateiname der Aufnahme), den Text je Sprache und einen Hash des Textes. Aendert sich ein Text
in strings.xml, zeigt der Test `KiloVoiceTest`, dass die Aufnahme veraltet ist.
"""
import hashlib, json, re, sys
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res"
CATALOG = ROOT / "game-engine/src/main/kotlin/app/kiloquest/game/QuestCatalog.kt"
FIXED = ["kilo_intro", "kids_new_sticker", "mini_great", "kids_waiting"] + [f"kilo_praise_{i}" for i in range(1, 7)] + [f"kilo_retry_{i}" for i in range(1, 5)]
# Hoerspiele, Teamziel, Anpassung, Hoerserie (nur Saetze ohne Platzhalter; die Folgen mit Zielort bleiben bei der Sprachausgabe)
FIXED += [
    "game_menu", "game_stopped", "game_giveup", "game_nothing", "game_end", "game_chain_intro", "game_chain_concede", "game_letter_intro",
    "game_letter_used", "game_rhyme_intro", "game_rhyme_again", "game_story_intro", "game_story_prompt", "audio_story_end",
    "team_goal_25", "team_goal_50", "team_goal_75", "team_goal_100", "kilo_adapt_easier", "kilo_adapt_harder", "kilo_adapt_calm",
    "series_wait", "series_done", "stopover_resume", "toll_check",
] + [f"audio_story_start_{i}" for i in range(1, 5)] + [f"audio_story_next_{i}" for i in range(1, 5)]
LANGS = {"en": RES / "values/strings.xml", "de": RES / "values-de/strings.xml"}


def load(path):
    out = {}
    for el in ET.parse(path).getroot().iter("string"):
        out[el.get("name")] = "".join(el.itertext()).replace("\\'", "'").replace('\\"', '"').replace("\\n", "\n")
    return out


def h(s):
    return hashlib.sha256(s.encode("utf-8")).hexdigest()[:12]


def main():
    strings = {l: load(p) for l, p in LANGS.items()}
    src = CATALOG.read_text(encoding="utf-8")
    quests = re.findall(r'\bt\("([a-z_]+)",\s*\w+,\s*setOf\([^)]*\)(?:,\s*(CHECK|COUNT|CHOICE))?(?:,\s*(\d+))?', src)
    lines = []
    for qid, kind, opts in quests:
        key = f"quest_{qid}"
        lines.append(("quest", key, {l: strings[l][key] for l in strings}))
        if kind == "CHOICE" and opts:
            def full(l):
                shapes = [strings[l]["shape_circle_blue"], strings[l]["shape_square_orange"], strings[l]["shape_triangle_green"]]
                o = ". ".join(f"{shapes[i % 3]}: {strings[l][f'{key}_opt{i + 1}']}" for i in range(int(opts)))
                return f"{strings[l][key]} {o}"
            lines.append(("quest_opts", key + ".opts", {l: full(l) for l in strings}))
    for key in FIXED:
        lines.append(("fixed", key, {l: strings[l][key] for l in strings}))
    data = [{"id": i, "group": g, "text": t, "hash": {l: h(t[l]) for l in t}} for g, i, t in lines]
    for d in data:
        for l, t in d["text"].items():
            if "%" in t:
                sys.exit(f"Platzhalter in {d['id']} ({l}): {t}")
    # Manifest fuer die App: Hash des gesprochenen Textes -> Id der Aufnahme (nur vorhandene Dateien)
    assets = ROOT / "app/src/main/assets/kilo_voice"
    lines_by_lang = {}
    for lang in LANGS:
        entry = {}
        for d in data:
            if (assets / lang / f"{d['id']}.ogg").exists():
                entry[d["hash"][lang]] = d["id"]
        lines_by_lang[lang] = entry
    assets.mkdir(parents=True, exist_ok=True)
    (assets / "manifest.json").write_text(
        json.dumps({"version": 1, "voice": "Finn Fable (ElevenLabs)", "lines": lines_by_lang}, ensure_ascii=False, indent=1, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    out = ROOT / "tools/voice/lines.json"
    out.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    chars = sum(len(t) for d in data for t in d["text"].values())
    print(f"{len(data)} Zeilen, {chars} Zeichen (beide Sprachen) -> {out}")


main()
