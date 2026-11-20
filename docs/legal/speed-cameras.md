# Blitzerwarnung: Rechtslage und Länder-Policy

**Keine Rechtsberatung.** Dies ist eine Recherche für die Entscheidung, ob KiloQuest in einem Land vor Geschwindigkeitskontrollen warnen darf.
Ergebnis (Stand der Recherche: Juni 2026): **In keinem Land ist die Warnfunktion belastbar als erlaubt belegt. Daher ist sie überall aus.**
Das **Tempolimit-Schild** (Anzeige und Überschreitungs-Hinweis) ist davon unabhängig und überall aktiv.

## Warum „aus“ der sichere Standard ist
- Die Quellen widersprechen sich oft; viele sind kommerzielle Seiten von App-Anbietern. Amtliche Aussagen fehlen für die meisten Länder.
- Die Lage ändert sich: Der EuGH hat am 16.06.2026 (C-188/24, C-190/24, Coyote) entschieden, dass Mitgliedstaaten Blitzer-Meldedienste
  anderer EU-Länder einschränken dürfen (Herkunftslandprinzip steht dem nicht entgegen). Neue nationale Verbote sind damit leichter möglich.
- „Nur feste Anlagen“ macht eine Warnung nicht automatisch legal (siehe Deutschland, Schweiz, Frankreich).

## Stand je Land (Recherche)
| Land | Status | Befund |
|---|---|---|
| DE | Verbot | Fahrer dürfen kein Gerät/keine App betreiben, die vor Kontrollen warnt (§ 23 Abs. 1c StVO); gilt laut ADAC auch für Apps, die ein Beifahrer bedient. |
| CH | Verbot | Warnsysteme sind nicht zulässig; Anbieter wie Waze/TomTom führen dort keine Kamerastandorte. Teils ist schon der Besitz beschränkt. |
| FR | eingeschränkt | Nur allgemeine „Gefahrenzonen“ ohne genaue Position erlaubt; Detektoren verboten. |
| SE | unklar | Geräte, die vor laufender Überwachung warnen sollen, sind verboten (Lag 1988:15); für Apps mit Kamerastandorten gibt es widersprüchliche Angaben, die Polizei kritisiert nutzergemeldete Kontrollen. |
| DK, FI, NO | unklar | Detektoren verboten; zu Apps widersprechen sich die Quellen. |
| NL | unklar | Meist als erlaubt beschrieben, aber nur mit älteren/unscharfen Quellen; EuGH-Urteil könnte Verbote auslösen. |
| BE, AT, IT, ES, PL | unklar | Quellen widersprechen sich (z. B. AT: einmal „§ 10a StVO verbietet“, einmal „erlaubt“; ES: DGT unterscheidet feste Warner (zulässig) von Detektoren/Inhibitoren (verboten) und will nutzerbasierte Warner verbieten). |

Quellen (Auswahl, zu prüfen, nicht als Rechtsgrundlage gedacht): ADAC „Radarwarner und Blitzer-Apps in Deutschland und im Ausland“;
TomTom „Are speed camera warnings legal?“; VIAS-Bericht „Warning systems for speed cameras“ (2023); IETL „Radar warning apps in road traffic“;
EuGH-Pressemitteilung zu C-188/24 und C-190/24 (curia.europa.eu); DGT-Hinweise (Motorpasión 2021); BilKoll zu Lag 1988:15; AVROTROS Radar zu Flitsmeister.

## Wann ein Land freigeschaltet wird
Ein Land wird nur in `CameraPolicy` (`:nav-core`) auf `DANGER_ZONE_ONLY` oder `FIXED_CAMERAS` gesetzt, wenn
1. eine **amtliche** Quelle (Gesetzestext, Behörde) oder eine anwaltliche Kurzprüfung vorliegt,
2. das Prüfergebnis mit Datum und Quelle in dieser Datei oder in `docs/legal/cameras-<land>.md` steht,
3. `verifiedOn` in der Regel gesetzt ist (ein Test erzwingt das) und
4. das Land kein Verbot hat (Länder mit Verbot sind immer aus).

Datenquelle bei Freischaltung: OSM `highway=speed_camera` (ODbL), nur feste Anlagen, keine mobilen Kontrollen, keine Crowd-Meldungen.
Warnung nur als Audio (Fahrsicherheit), nie als Bedienaufgabe.
