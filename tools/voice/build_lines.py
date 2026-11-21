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
# Kilo-eigene feste Ansagen (Sprachbefehle, Maut, Klo, Reiseuebelkeit, Pausen, Stopp); Navigation (arrived, rerouting, reroute_speech) bleibt Geraete-Stimme
FIXED += [
    "cmd_no_trip", "cmd_unmuted", "cmd_muted", "cmd_unknown_road", "cmd_unknown", "cmd_no_limit", "cmd_navigation_ended", "cmd_help", "cmd_voice_unavailable",
    "toll_intro", "toll_none", "toll_checking", "toilet_searching", "toilet_none", "toilet_none_open", "toilet_arrived", "toilet_resume",
    "stopover_none", "intent_unsupported", "intent_unsupported_stop", "intent_search_done", "intent_not_found", "game_need_voice",
    "sickness_prompt_1", "sickness_prompt_2", "sickness_prompt_3", "break_arrived",
] + [f"toll_{c}" for c in "at ch cz sk si hu bg ro fr it es pt hr gr pl no dk se nl".split()]
# Teilclips (Zahlen und Satzstuecke, siehe VoiceParts in :game-engine); Texte stehen hier, nicht in strings.xml
PARTS = {
    "part_limit_is": {"de": "Das Tempolimit ist", "en": "The speed limit is"},
    "part_weather_at_dest": {"de": "Wetter am Ziel:", "en": "Weather at the destination:"},
    "part_degrees": {"de": "Grad.", "en": "degrees."},
    "part_minus": {"de": "minus", "en": "minus"},
}
CONDITIONS = "clear cloudy fog drizzle rain snow showers storm".split()
MAX_NUMBER = 130
DE_UNITS = ["null", "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun", "zehn", "elf", "zwölf", "dreizehn", "vierzehn", "fünfzehn", "sechzehn", "siebzehn", "achtzehn", "neunzehn"]
DE_TENS = {2: "zwanzig", 3: "dreißig", 4: "vierzig", 5: "fünfzig", 6: "sechzig", 7: "siebzig", 8: "achtzig", 9: "neunzig"}
EN_UNITS = ["zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"]
EN_TENS = {2: "twenty", 3: "thirty", 4: "forty", 5: "fifty", 6: "sixty", 7: "seventy", 8: "eighty", 9: "ninety"}


def de_num(n):
    if n < 20:
        return DE_UNITS[n]
    if n < 100:
        u, t = n % 10, n // 10
        return DE_TENS[t] if u == 0 else ("ein" if u == 1 else DE_UNITS[u]) + "und" + DE_TENS[t]
    rest = n - 100
    return "hundert" if rest == 0 else "hundert" + ("eins" if rest == 1 else de_num(rest))


def en_num(n):
    if n < 20:
        return EN_UNITS[n]
    if n < 100:
        u, t = n % 10, n // 10
        return EN_TENS[t] if u == 0 else EN_TENS[t] + "-" + EN_UNITS[u]
    rest = n - 100
    return "one hundred" if rest == 0 else "one hundred " + en_num(rest)


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
    for pid, t in PARTS.items():
        lines.append(("part", pid, t))
    for c in CONDITIONS:
        lines.append(("part", f"wcond_{c}", {l: strings[l][f"weather_cond_{c}"] for l in strings}))
    for n in range(MAX_NUMBER + 1):
        lines.append(("num", f"num_{n}", {"de": de_num(n), "en": en_num(n)}))
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
