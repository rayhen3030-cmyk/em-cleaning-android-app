# Mitarbeiter-App: Reparatur und sichere Inbetriebnahme

## Stand

Die Änderungen bauen auf PR #1 auf. Kein Merge, kein Release, kein Firebase-Deployment und keine Änderung produktiver Mitarbeiterdaten wurden durchgeführt. Die derzeit produktiven Firebase-Regeln sind ohne Firebase-Kontozugang nicht geprüft. Der Quellcode allein sichert die laufende Installation noch nicht ab.

## Ursache und Änderung

Bisher war Firebase Authentication anonym. Eine im APK enthaltene Admin-PIN und aus Firestore gelesene PIN-Hashes wurden ausschließlich im Client geprüft. Dadurch konnte Firebase keinen Mitarbeiter zuverlässig identifizieren. Die Dokumentabfrage lud die komplette Sammlung und filterte erst im Client; sichere Regeln müssen eine solche Abfrage abweisen.

Jetzt erfolgt die Anmeldung mit einem persönlichen Firebase-E-Mail/Passwort-Konto. Vertrauenswürdige Custom Claims verbinden die Auth-UID mit der vorhandenen Mitarbeiter-Dokument-ID (`employeeId`) oder geben `admin: true` frei. Keine Selbstregistrierung und keine Rollenvergabe durch den Client. Alte PINs werden nicht mehr für die Anmeldung verwendet. Bestehende Mitarbeiterdatensätze werden nicht migriert oder gelöscht.

Die Mitarbeiterabfrage verwendet `whereEqualTo("employeeId", eigeneKennung)`. Nicht zugeordnete Dokumente sind für Mitarbeiter gesperrt. Dateien liegen unter `documents/employees/<employeeId>/<file>`. Firestore und Storage erzwingen die Zugehörigkeit unabhängig vom Client. Nur Administratoren laden zugeordnete Dateien hoch. URL-only-Dokumente werden nicht mehr geöffnet, da ein externer Link die Storage-Anmeldung umgehen kann. Bestandsdateien mit falschen oder fehlenden Zuordnungen benötigen eine gesondert freigegebene Korrektur.

Abmeldung beendet die Firebase-Sitzung, entfernt Dokumentdateien aus dem App-Cache und erstellt die Oberfläche neu. Dokumentdownloads prüfen einen Sitzungswechsel vor dem Öffnen. Firestore-Persistenz ist deaktiviert. Die WebView-Bridge bleibt auf die gebündelte App-Seite beschränkt; externe Links dürfen nur HTTPS verwenden.

## Android und Tests

Android 7+ (API 24), Ziel-API 35. `gradle assembleDebug testDebugUnitTest lintDebug` baut eine Debug-APK und führt die vorhandenen Android-Prüfungen aus. Es gab im Ausgangsprojekt keine nativen Unit- oder Instrumentierungstests. GitHub Actions führt zusätzlich die Firebase-Tests aus und speichert APK/Prüfberichte als Artefakt. Kein Store-Upload und keine Release-Signierung. Maßgeblich für den aktuellen Commit sind die GitHub-Checks in PR #1; die Berichte enthalten auch verbliebene Lint-Warnungen.

Die Debug-Variante ist ausdrücklich ein Emulator-Testbuild: separates `google-services.json` für `demo-em-cleaning`, Firebase Auth/Firestore/Storage ausschließlich über `10.0.2.2`, kein echter FCM-Tokenabruf. Produktivkonfiguration bleibt für Release bestehen. Der Testbuild läuft nicht gegen die produktiven Mitarbeiterdaten und zeigt „E-M Cleaning TEST“ an. Auf einem physischen Telefon ist `10.0.2.2` nicht erreichbar; dafür ist eine gesonderte Staging-Konfiguration erforderlich.

Lokal starten:

```sh
npm ci
npm ci --prefix firebase/functions
npx firebase emulators:start --project demo-em-cleaning --only auth,firestore,storage
# In zweitem Terminal:
node firebase/functions/seed-emulator.js
```

Auf einem Android-Emulator mit Google APIs die Debug-APK installieren. Konten: `alice@example.test`, `bob@example.test`, `admin@example.test`; Passwort jeweils `Demo-Passwort!2026`. Diese Konten und Dokumente existieren ausschließlich im Emulator. Alice und Bob müssen jeweils genau ihre zwei Testdokumente sehen. Nach Abmeldung darf keine vorherige Liste sichtbar sein. Die Verwaltung kann beide Mitarbeiter sehen.

Automatische Prüfungen:

```sh
npm test
npm run test:rules
```

Vier Tests für Ereignisrouting, acht Tests mit echten Firestore/Storage-Emulatoren und zwei Integrationstests mit echter E-Mail/Passwort-Anmeldung gegen den Auth-Emulator bestanden lokal. Insgesamt 14 Tests erfolgreich. Die Integrationstests prüfen beide Mitarbeiterkonten, Testvertrag und Testabrechnung einschließlich Dateidownload, Fremdzugriff, Abmeldung, falsches Passwort und Admin-Anmeldung. Abgedeckt: Eigentümerzugriff, Fremdzugriff, ungefilterte Abfragen, anonyme/nicht zugeordnete Konten, Dateiuploads, Rollenmanipulation, gefälschter Nachrichtensender, Anträge, Admin-Dateien und Token-Eigentümerschaft. Die negativen Tests erzeugen erwartete PERMISSION_DENIED-Logs. Auch das Anlegen der synthetischen Demo-Konten und Testdateien wurde erfolgreich ausgeführt. Keine produktiven Dienste werden für diese Tests angesprochen.

## Push

`PushService` registriert pro Auth-UID Geräte, erneuert Tokens und meldet sie bei Logout ab. Android 13+ fragt nach Benachrichtigungsberechtigung. Vier Cloud-Functions-Auslöser:

- Neuer Urlaubsantrag / neues Ticket → Verwaltung; Statusänderung → betroffener Mitarbeiter.
- Neue Nachricht → jeweils Gegenseite im zugeordneten Mitarbeitergespräch.
- Neues oder aktualisiertes Dokument → ausschließlich zugeordneter Mitarbeiter.

Serverseitige Empfängerzuordnung in `pushRecipients/<uid>` wird vom Provisionierungsskript geschrieben. Der Versand prüft zusätzlich aktuelle Auth-Claims und gesperrte Konten. Benachrichtigungen sind generisch und enthalten keine Namen, Texte, Dateilinks, Löhne oder Personalinhalte. Android zeigt nur Nachrichten für die aktuell angemeldete UID an. Ungültige Tokens werden entfernt. Trigger werden bei Fehlern wiederholt; doppelte Zustellungen sind möglich, Android verwendet dieselbe Benachrichtigungskennung pro Ereignis. Offline-Zustellung ist auf eine Stunde begrenzt, ein erzwungenes Beenden oder OS-Energiesparen kann sie verhindern/verzögern. Kein garantierter Zustellnachweis.

Echter FCM-Versand, Hintergrundempfang, Berechtigungsablehnung, Tokenwechsel und Abmeldung müssen mit einer Staging-App auf mindestens einem echten Android-Gerät getestet werden. Debug deaktiviert FCM absichtlich. Für einen Staging-Build müssen ein separates Firebase-Testprojekt und dessen passende App-Konfiguration bereitgestellt werden. Cloud Functions erfordern ein dafür freigeschaltetes Firebase-Projekt und können Kosten verursachen. Die Funktionsregion `europe-west3` muss vor Deployment zur Projekt-/Datenbankregion passen.

## Firebase-Kontofreigabe und Ablauf

1. Firebase-Testprojekt freigeben; Email/Password aktivieren, Passwort-Richtlinie und Schutz vor E-Mail-Ermittlung prüfen. Produktive Regeln/Auth-Einstellungen vorher separat nur lesend erfassen. Anonyme Anmeldung bei der späteren Umstellung abschalten; neue Regeln verweigern anonymen Zugriff bereits unabhängig davon.
2. Persönliche Testkonten über Firebase Auth anlegen, Passwörter über einen sicheren persönlichen Prozess vergeben; niemals Passwörter oder Service-Account-Schlüssel ins Repository oder den Chat kopieren.
3. Konten ausschließlich administrativ zuordnen. Trockenlauf schreibt nichts:

```sh
node firebase/functions/provision-account.js --project TESTPROJEKT --uid AUTH_UID --employee MITARBEITER_DOKUMENT_ID
# Nach Prüfung desselben Befehls: --apply ergänzen.
# Verwaltung: statt --employee ... die Option --admin.
```

`--apply` setzt Auth-Claims, widerruft Refresh-Tokens und schreibt die separate Push-Zuordnung. Es ändert keine `employees`-Dokumente. Vor Rollenwechsel oder Neuzuordnung vorhandene Zuordnungen prüfen; bestehende Mitarbeiterkennungen müssen eindeutig sein und aus Buchstaben, Zahlen, Unterstrich oder Bindestrich bestehen. Keine automatische Übernahme alter PINs.

4. Regeln und Functions zunächst im Testprojekt bereitstellen. Anmeldung, Datenzugriff, Downloads und Push mit synthetischen Daten Ende zu Ende prüfen. Erst danach über produktive Umstellung entscheiden.
5. Bestandsprüfung: Stimmen `employees`-Dokument-ID, Claim und `companyDocuments.employeeId` überein? Entspricht `storagePath` dem Besitzer? Sind Mitarbeiterdokumente bisher als „allgemein“ oder nur als URL gespeichert? Keine automatische Massenkorrektur.
6. Vor produktiver Freigabe alte öffentliche Storage-Regeln, externe Freigaben und bestehende Firebase-Download-Tokens prüfen. Bereits ausgegebene Token-URLs sind Bearer-Links und werden durch die neuen App-Abfragen nicht zurückgerufen. Ihr Widerruf bzw. eine Verschiebung bestehender Dateien ändert produktive Metadaten und benötigt eine gesonderte Freigabe. Ohne diese Bestandsprüfung lässt sich die Exklusivität alter Dokumente nicht bestätigen.
7. Neue Anmeldung und Regeln zusammen umstellen: alte PIN-Apps verlieren mit den neuen Regeln ihren Zugriff. Aktive Regeln sichern, getesteten Rückfallplan festlegen, Nutzer informieren, ausschließlich eine abgenommene Version verteilen. Dieses Repository führt keinen automatischen Deploy aus.

## iPhone

Es gibt keinen iOS-Client und kein Xcode-Projekt. Das Android-APK, die Java-Bridge und die ARCore-Messfunktion sind nicht direkt auf iPhones nutzbar. Möglich sind ein eigener Swift/WKWebView-Client mit Firebase-SDKs oder eine spätere plattformübergreifende Umsetzung. Ein WebView-Wrapper benötigt alle bisher nativen Bridge-Funktionen erneut, einschließlich Auth, Storage, PDF/Dateien und optional ARKit.

Firebase Auth, Firestore-Regeln, Dokumentpfade und Ereignisrouting sind wiederverwendbar. Für iOS sind ein Apple-Developer-Konto, Bundle-ID, Signierung, Firebase-iOS-Konfiguration, APNs-Schlüssel und Benachrichtigungsberechtigungen nötig. Der vorbereitete APNs-Datenversand ist nur eine Grundlage: stille Hintergrundbenachrichtigungen werden von iOS nicht zuverlässig oder bei jedem App-Zustand zugestellt. Sichtbare iPhone-Pushs benötigen einen gezielt implementierten und geprüften iOS-Empfänger samt passender APNs-Payload. Kein iPhone-Build und kein iPhone-Gerätetest wurden durchgeführt.

Referenzen: https://firebase.google.com/docs/rules/unit-tests ; https://firebase.google.com/docs/auth/admin/custom-claims ; https://firebase.google.com/docs/cloud-messaging/android/get-started ; https://firebase.google.com/docs/cloud-messaging/ios/get-started ; https://developer.apple.com/documentation/usernotifications/pushing-background-updates-to-your-app
