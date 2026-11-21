# Google Play: interner/geschlossener Test vorbereiten

Stand: Vorbereitung im Repo erledigt, die Schritte im Play-Konto und die Schlüsselerzeugung sind Handarbeit des Besitzers (nicht automatisierbar von hier).

## Was im Repo vorbereitet ist
- Ziel-API **36** (Pflicht für neue Apps/Updates seit 31.08.2026), `compileSdk` 36; Tests und Lint grün.
- Release-Build mit R8 und Ressourcen-Schrumpfung läuft (AAB ~44 MB); R8 meldet keine fehlenden Regeln. **Auf einem Gerät ist die Release-Variante nicht getestet.**
- Native Bibliotheken sind 16-KB-kompatibel (Pflicht für Ziel-API ≥ 35 auf neuen Geräten).
- Versionsnummern per `-PversionCode=N -PversionName=x.y.z`; Signatur nur über Umgebungsvariablen (`KILO_KEYSTORE_FILE`, `KILO_KEYSTORE_PASSWORD`, `KILO_KEY_ALIAS`, `KILO_KEY_PASSWORD`). Keystore-Dateien sind in `.gitignore`.
- GitHub-Workflow **Release (AAB für Google Play)** (`.github/workflows/release.yml`): baut ein signiertes Bundle als Artefakt.
- Eintragstexte DE/EN (`play-listing.md`), Grafiken (`store/`).

## Einmalig im Play-Konto (Besitzer)
1. Entwicklerkonto anlegen (einmalige Gebühr, Identitätsprüfung). **Für private Konten, die nach dem 13.11.2023 angelegt wurden, gilt: Vor dem Produktivstart braucht es einen geschlossenen Test mit mindestens 12 Testern, die 14 Tage lang durchgehend teilnehmen.** Ein *interner* Test (bis 100 Tester) geht sofort und ist davon nicht betroffen.
2. App anlegen: Name „KiloQuest: Familien-Navi“, Standardsprache Deutsch, App (kein Spiel), kostenlos.
3. **Upload-Schlüssel erzeugen** (ohne PC z. B. in einer GitHub Codespace oder Cloud-Shell): `tools/release/make_keystore.sh`; Datei und Passwörter sicher aufbewahren, nie ins Repo oder in den Chat.
4. In GitHub (Settings › Secrets and variables › Actions) vier Secrets setzen: `KILO_KEYSTORE_B64` (`base64 -w0 kiloquest-upload.jks`), `KILO_KEYSTORE_PASSWORD`, `KILO_KEY_ALIAS`, `KILO_KEY_PASSWORD`.
5. Actions › „Release (AAB für Google Play)“ › Run workflow (z. B. versionCode 1, versionName 0.1.0) → Artefakt `app-release.aab` herunterladen.
6. In der Play Console: Test › Interner Test › Release erstellen › AAB hochladen, **Play App Signing** aktivieren, Tester-E-Mail-Liste anlegen, Testlink an die Tester schicken.

## Formulare in der Play Console (Besitzer; Entwürfe)
- **Datenschutzerklärung:** öffentlich erreichbare URL nötig. `docs/legal/datenschutz.md` muss als öffentliche Seite bereitgestellt werden (z. B. eigene Webseite). *Offen.*
- **App-Zugriff:** Die App funktioniert ohne Routenserver nicht. Den Prüfern Adresse und einen Test-Token für einen Demo-Server nennen. *Offen: Demo-Server und Token.*
- **Datensicherheit** (Entwurf, vor dem Absenden mit dem tatsächlichen Verhalten abgleichen):
  - Standort (genau): wird an den eigenen Routenserver und an offene Dienste (Photon, Overpass, Open-Meteo, Wikipedia u. a.) gesendet → „erhoben“ und für App-Funktionen weitergegeben; keine Werbung, keine Analyse.
  - Audio: Spracherkennung standardmäßig nur auf dem Gerät; die optionale Cloud-Erkennung (Warnhinweis) nutzt den Dienst des Handys.
  - Fotos/Tagebuch/Kindernamen: nur auf dem Gerät, nicht hochgeladen.
  - Optionale Cloud-Stimme: Straßennamen und Sprache gehen an den eigenen Stimmen-Server und ElevenLabs.
  - Daten werden nicht verkauft; Löschen/Export in den Einstellungen.
- **Zielgruppe und Inhalt:** Entscheidung nötig. Die App richtet sich an Eltern/Fahrer und enthält eine Kinderansicht. Wird „Kinder unter 13“ als Zielgruppe gewählt, gilt die Richtlinie „Für Familien“ (strenge Auflagen für Daten und Dienste). Empfehlung für den Test: Zielgruppe „Erwachsene/Familien (ab 18, Eltern)“ wählen und die Kinderansicht als Funktion in der Beschreibung nennen; vor einer öffentlichen Veröffentlichung rechtlich prüfen. *Offen.*
- **Berechtigungen und Vordergrunddienste:** Standort (im Vordergrund und Vordergrunddienst „Standort“), Mikrofon (Sprachantworten, Spiele, Befehle), Kamera (Fotostory, nur auf Wunsch), Bluetooth (Geräte koppeln), Benachrichtigungen, Vordergrunddienst-Typen „Standort“ und „verbundenes Gerät“. In der Console „Vordergrunddienst-Typen“ mit Begründung deklarieren.
- **Inhaltseinstufung** (Fragebogen): keine Gewalt, keine Nutzerinhalte, keine Käufe, keine Werbung.

## Android Auto
- Kategorie Navigation; im Test über die Test-Track-Installation. Der Auto-Dienst erlaubt derzeit **alle Hosts** (`HostValidator.ALLOW_ALL_HOSTS_VALIDATOR`). **Vor einer öffentlichen Veröffentlichung** durch die offizielle Host-Allowlist von Google ersetzen (Signatur-Hashes aus der Android-for-Cars-Dokumentation entnehmen und gegen die Quelle prüfen; nicht aus dem Gedächtnis eintragen). Navigations-Apps für Android Auto durchlaufen in Play eine zusätzliche Prüfung. *Offen.*
- Bei APK-Installation außerhalb von Play zeigt Android Auto die App nur mit „Unbekannte Quellen“ (Selbsttest erklärt es). Über Play-Test-Installation entfällt das.

## Rechtliches vor einer öffentlichen Veröffentlichung
- **ElevenLabs:** Nutzungsrechte des Tarifs für die Verteilung der aufgenommenen Clips in einer veröffentlichten App prüfen.
- **OpenStreetMap/OpenFreeMap** (ODbL) und weitere Quellen: Namensnennung ist in der App sichtbar; Lizenztext unter Einstellungen › Lizenzen.
- Eigener Server/Proxy: Betreiber = Nutzer; in der Datenschutzerklärung benannt.

## Noch nicht getestet
Release-Variante auf einem Gerät, Installation über Play, Android Auto aus Play heraus, Verhalten mit Ziel-API 36 im Alltag (z. B. Rückwärts-Geste, Großbildschirme), echte Screenshots der Navigation.
