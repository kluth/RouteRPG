# Spec 008: Freihändige Sprachbedienung (Gemini/Assistant-Intents und Sprachbefehle)

Quelle der Intent-Formate: Android-Entwicklerdokumentation „Implement navigation app intents“ (Assistant, Gemini; Android Auto, Android Automotive, Handy).
Der Fahrer bekommt nur Audio-Antworten und muss nichts bedienen (AGENTS.md Regel 4).

## 1. Intents von Gemini/Google Assistant (Handy und Android Auto)
Parser `NavIntents` (`:nav-core`, getestet), Eintrittspunkte `MainActivity` (Handy, `singleTop`) und `KiloQuestSession` (Auto):
| Intent | Format | Verhalten |
|---|---|---|
| Navigation starten | Aktion `androidx.car.app.action.NAVIGATE` (Auto) bzw. `android.intent.action.NAVIGATE`, `geo:0,0?q=Ziel` oder `geo:lat,lon` | Ort suchen (Photon), Route berechnen, Navigation sofort starten, Ziel per Sprache bestätigen |
| `intent=directions` | wie oben | nur Route zeigen |
| `intent=add_a_stop` | wie oben | wird dem Nutzer als „nicht unterstützt“ gemeldet (Vorgabe von Google) |
| `avoid=f,h,t`, `mode` | Parameter | Fähren/Autobahnen/Maut meiden (Valhalla `use_ferry`/`use_highways`/`use_tolls` = 0), einmalig bis zur nächsten manuellen Zielwahl |
| Suche | `android.intent.action.VIEW`, `geo:0,0?q=restaurants+nearby` | Suche, Ergebnisse für den Beifahrer sichtbar, Sprachhinweis |
| Alte Form | `google.navigation:q=Ziel` | wie Navigation starten |
| Aktionen | `android.intent.action.VIEW`, `geo.action:?act=...` (auch `.offline`) | siehe unten |

Unterstützte `act`-Werte: `mute`, `unmute`, `exit_navigation`, `eta`, `time_to_destination`, `distance_to_destination`, `distance_to_next_turn`,
`time_to_next_turn`, `query_next_turn`, `query_destination`, `query_current_road`, `avoid_/allow_` `tolls|highways|ferries`.
Alles andere (`report_*`, `show_*`, `follow_mode` …) wird per Sprache als „nicht unterstützt“ gemeldet.

## 2. Eigene Befehle
- Deep Links `kiloquest://cmd/<toilet|break|story|mute|unmute|eta>` für Routinen/Automatisierung (z. B. Lenkradtaste über eine Automatisierungs-App).
- Sprachbefehle (`VoiceCommands`, Deutsch und Englisch): Klo-Alarm, Pause, Geschichte, Ansagen aus/an, „Wie lange noch?“, nächste Abbiegung, Tempolimit,
  „Wohin fahren wir?“, Navigation beenden, Hilfe.
  - Knopf „Sprachbefehl“ im Beifahrer-Menü: einmal zuhören, Befehl ohne Anrede.
  - Einstellung „Sprachbefehle mit Kilo“ (Standard aus): hört während der Fahrt zu, reagiert nur auf „Kilo, …“. Erkennung **nur auf dem Gerät**
    (Android 12+, Offline-Sprachpaket), nichts wird gespeichert oder gesendet; nicht beim Sprechen der App und nicht während einer Quiz-Antwort.
- Neue Einstellungen: Mautstraßen, Autobahnen, Fähren meiden (für jede Route).

## Testen
- Handy: `adb shell am start -a androidx.car.app.action.NAVIGATE -d "geo:0,0?q=Rovaniemi"`, `adb shell am start -a android.intent.action.VIEW -d "geo.action:?act=eta"`,
  `adb shell am start -a android.intent.action.VIEW -d "kiloquest://cmd/toilet"`.
- Auto: Desktop Head Unit (DHU) mit Assistant bzw. Gemini; das Verhalten ist dort zu prüfen.

## Grenzen
- Nicht unterstützt: Zwischenstopps, Meldungen (Unfall, Gefahr, Polizei …), Kartenansichten. Gemini-Abfrage des Navigationsstatus (`NavigationManager`-Metadaten) fehlt.
- Dauer-Zuhören läuft nur, solange die App im Vordergrund ist; kein Wake-Word-Modul (nur Erkennung der Anrede im erkannten Text). Der Systemton beim
  Start der Erkennung lässt sich auf manchen Geräten nicht abschalten.
- Reihenfolge der Rückmeldung und das Verhalten von Gemini auf echten Geräten sind ungetestet.
