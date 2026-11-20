# Spec 011: Maut-Lotse, Funkloch-Vorwarnung, Spuranzeige im Auto, Notfall-Karte (Block 3 aus Spec 009)

Fahrer bekommt nur Audio; Anzeigen sind für den Beifahrer oder die offiziellen Auto-Elemente.

## Maut-/Vignetten-Lotse (`TollGuide`, `:nav-core`)
- Tabelle je Land (Pkw): Vignette (AT, CH, CZ, SK, SI, HU, BG, RO), Mautstraßen (FR, IT, ES, PT, HR, GR, PL), Sonderfälle (NO AutoPASS, DK Brücken, SE Öresund/Staugebühr, NL Westerscheldetunnel), keine Maut (DE, FI, BE, LU).
- Länder der Route: alle 80 km (Grobschätzung ohne Netz, sonst Photon; Länder zwischen zwei Proben können fehlen, z. B. sehr kurze Durchfahrten).
- Ansage einmal beim Start der Fahrt (8 s nach der ersten Anweisung), auf Sprachbefehl („Kilo, Maut/Vignette“) und als Karte im Beifahrer-Menü mit offizieller Webseite (nur wo sicher bekannt) und Stand der Tabelle (`TollGuide.AS_OF`).
- **Keine Rechtsberatung**; Preise/Regeln ändern sich. Tabelle bei jeder Reise prüfen und `AS_OF` aktualisieren.

## Funkloch-Vorwarnung (`RemoteStretches`/`RemoteWarner`)
- Es gibt keine freie, offline nutzbare Abdeckungskarte. Stattdessen: Ortschaften (OSM `place`) entlang der Route (Overpass, 250-km-Fenster); Lücken ab 60 km = „dünn besiedelt, Netz kann fehlen“.
- Ansage 25 km vor dem Stück (einmal): Tanken, Pause, Geschichten laden; ohne Reise-Paket zusätzlich der Tipp, eines zu laden. Schlägt ein Overpass-Fenster fehl, gibt es keine Aussage (keine falschen Lücken).
- Nur ein **Hinweis**, keine Zusage. Die Navigation selbst läuft lokal weiter; Neuberechnung braucht Netz.

## Spuranzeige im Auto
- `Step.addLane`/`LaneDirection` plus selbst gezeichnetes Spurbild für den aktuellen Schritt (Spuren aus der OSRM-Zweitanfrage, nur bei Einstellung „Spurhinweise“). Der Host zeigt sie nativ; der eigene Canvas bleibt zusätzlich. Fehler beim Bauen lassen den Schritt ohne Spuren.

## Notfall-Karte
- Sprachbefehl („Kilo, Notfall“) oder Knopf im Beifahrer-Menü: Notrufnummer des Landes (112, Ausnahmen GB 999, US/CA 911, AU 000, NZ 111), Standort als Text (Straße, Koordinaten, OpenStreetMap-Link, teilbar), nächste Krankenhäuser (Overpass, bis 30 km), Knopf öffnet nur das Telefon (wählt nie selbst).
- Dialog liegt über jedem Bildschirm. **Kein Ersatz für den Notruf.**

## Offen / ungetestet
- Ob die Lane-Elemente im echten Auto/DHU richtig aussehen (Größe, Kontrast); Spurbild ist ein Entwurf.
- Genauigkeit der Funkloch-Hinweise in Lappland (Ortsdichte ≠ Netzabdeckung); Photon/Overpass-Last bei langen Routen (35 Proben, 12 Fenster).
- Krankenhaus-Suche ohne Netz nicht möglich; Satelliten-SOS-Hinweis fehlt noch.
