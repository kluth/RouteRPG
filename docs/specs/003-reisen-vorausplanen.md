# Spec 003: Reisen fest vorausplanen (Monate im Voraus)

Ziel: Fahrten mit Datum speichern, feste Buchungen mit Check-in-Tag und Nächten eintragen und die Route so planen,
dass man für die Unterkünfte nicht abweichen muss.

## Daten (lokal, kein Cloud-Dienst)
- `PlannedTrip`: Name, Starttag (Epochentag), Start, Ziel, Liste `PlanStay` (Name, Ort, Check-in-Tag, Nächte).
- Speicherung als JSON in SharedPreferences (`PlanStore`, in `:nav-core`, getestet).

## Planung (`Itinerary`, reine Logik)
- Ankerfolge: Start → Buchungen (nach Check-in sortiert) → Ziel. Jede Etappe wird über Valhalla mit allen Buchungen als
  Zwischenziele berechnet (die Route führt also durch die Unterkünfte).
- Verfügbare Tage je Etappe = Check-in des nächsten Ankers − Check-out des vorherigen + 1. Benötigte Fahrtage = Fahrzeit / Tagesbudget.
- Gefahren wird so, dass die Etappe am Check-in-Tag endet; überzählige Tage bleiben als freie Tage am vorigen Ort.
- Probleme: Buchungen überschneiden sich; zu wenige Tage für eine Etappe (mit fehlenden Tagen).
- Unterwegs fehlende Übernachtungen bekommen Vorschlagspunkte (gleichmäßig geteilt, mit Datum). Dort lässt sich per
  Unterkunftssuche eine Buchung anlegen; die Route wird neu geplant und läuft dann durch sie.
- Umweg: Zusatzfahrzeit der Route über alle Buchungen gegenüber der direkten Route, Abstand jeder Buchung zur direkten Route.

## Nutzung
- Die Reise lässt sich jederzeit als aktuelle Reise (Etappen) übernehmen.
- Keine Erinnerungen/Benachrichtigungen in dieser Version; die Liste zeigt „in N Tagen“.
