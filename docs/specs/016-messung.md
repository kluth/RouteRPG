# 016 – Messung der Namenabdeckung (Schritt 1)

Quelle: OSM-Auszug Nordrhein-Westfalen (openstreetmap.fr, Stand 21.11.2026, ODbL), gemessen mit `tools/voice/measure_names.py --pbf`.
Overpass und Geofabrik waren aus der Entwicklungsumgebung nicht erreichbar; der Auszug kam über `download.openstreetmap.fr`.

## Straßennamen (befahrbare Wege mit Namen)
- 662.242 Wege, **96.838 verschiedene Namen**.

| Häufigste N Namen | Anteil der Wege |
|---|---|
| 10 | 2,5 % |
| 50 | 6,9 % |
| 100 | 10,4 % |
| 500 | 22,6 % |
| 1.000 | 29,6 % |
| 5.000 | 50,4 % |
| 20.000 | 73,8 % |

- Häufigste: Hauptstraße (3.539), Bahnhofstraße (3.085), Kölner Straße, Bergstraße, Münsterstraße, Schulstraße …
- Endungen: -straße 53 %, -weg 14 %, -ring 2 %, -hof/-allee/-platz je ~1 %.
- 44.323 verschiedene Stämme (Name ohne Endung) gegenüber 96.838 Namen: **Zerlegen in Stamm + Endung spart nur etwa die Hälfte** und klingt zusammengesetzt unnatürlicher.

## Orte (Knoten mit Namen)
- 8.448 verschiedene Ortsnamen: 30 Städte, 300 Städtchen, 2.903 Dörfer, 1.640 Stadtteile, 4.056 Weiler.

## Was das heißt
- Eine feste Liste erreicht die ersten 50 % mit etwa 5.000 Namen, 74 % brauchen schon 20.000. Ein vollständiger Satz wäre in NRW allein ~97.000 Namen. **Die Annahme der Spec bestätigt sich: Listen allein reichen nicht, der Long-Tail ist lang.**
- Kosten (Annahme 1 Credit je Zeichen, ~14 Zeichen je Name): 5.000 Namen ≈ 70.000 Credits, 20.000 Namen ≈ 280.000 Credits, also fast ein Monatskontingent (363.000) für 74 %.
- **Folgerung:** Das Pro-Fahrt-Stimmenpaket (Stufe 3) bleibt der Kern. Statische Namenpakete lohnen nur für die häufigsten ~1.000–5.000 Straßen je Land und die größeren Orte (Städte und Städtchen ≈ 330 in NRW); Dörfer und Weiler kommen aus dem Pro-Fahrt-Paket.

## Grenzen der Messung
- Gezählt sind **Wegabschnitte**, nicht Ansagen: Lange Straßen bestehen aus mehreren Abschnitten und werden überzählt; angesagt werden nur Abbiegungen. Die echte Abdeckung der Ansagen kann abweichen.
- Nur NRW (Stadt und Land gemischt), nicht ganz Deutschland, nicht Österreich/Schweiz/Dänemark/Schweden.
- Nicht gemessen: Namen je Fahrt (30–80 sind eine Schätzung), Aussprachequalität, Kosten pro Fahrt.
- Ein Vornamen- oder Nachnamen-Datensatz wurde nicht gemessen (offene Quelle noch zu klären).

## Nächste Messungen
- Namen je Beispielroute über den Routenserver (Ansagen statt Abschnitte).
- Weitere Länder aus `download.openstreetmap.fr/extracts/europe/<land>/`.
