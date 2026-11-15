# Streamer Remote

Android-TV-App für den Xiaomi Mini LED S55 im Basic-TV-Modus. Sie empfängt Tasten der Xiaomi-Fernbedienung über eine Bedienungshilfe und sendet ausgewählte Aktionen per authentifiziertem ADB im lokalen Netz an einen Google TV Streamer. Die App läuft auf dem Xiaomi; auf dem Streamer wird keine zusätzliche App installiert. Google-Anmeldung, HTTP-Server, dauerhaft laufender Mac und Sprachassistent sind nicht erforderlich.

## Geprüfter Stand (v0.15)

- Auf dem Streamer-HDMI-Bildschirm: kurzes Home sendet Home an den Streamer und hält bzw. stellt die HDMI-Ansicht wieder her. Langes Home öffnet die Xiaomi-Startseite.
- Auf der Xiaomi-Oberfläche: Home führt zur Xiaomi-Startseite.
- Netflix, Prime Video und YouTube können jeweils eine frei gewählte, auf dem Streamer installierte App öffnen.
- Apps öffnet auf dem Streamer die App-Übersicht des Streamers und auf der Xiaomi-Oberfläche die Xiaomi-Apps. Langes Apps auf Xiaomi startet Streamer Remote direkt.
- Die Eingangstaste (Code 178) öffnet die Xiaomi-Eingangsauswahl direkt.
- Die Tasten Home 3, Netflix 193, Prime Video 194, YouTube 190 und Apps 284 wurden auf dem Gerät empfangen und getestet. Die Eingangstaste wurde ebenfalls mit der echten Fernbedienung getestet.
- Die ADB-Identität liegt im privaten dauerhaften App-Speicher. Cache-Löschen und Prozessneustarts erzeugen damit keinen neuen Schlüssel. Eine Deinstallation oder das Löschen von App-Daten entfernt ihn weiterhin.
- GitHub Actions verwendet einen geschützten, gleichbleibenden Signaturschlüssel. Updates können über die bestehende App installiert werden und behalten Einstellungen und Bedienungshilfe-Zustand.

Die Erkennung „Streamer-Bildschirm“ nutzt die Xiaomi-Live-TV/HDMI-Oberfläche. Andere TV-Eingänge wurden dafür noch nicht separat geprüft.

## APK installieren oder aktualisieren

Den neuesten erfolgreichen [Build debug APK](https://github.com/MaWeXi/streamer-remote/actions/workflows/build-apk.yml) öffnen, das Artefakt `streamer-remote-debug-apk` herunterladen und entpacken. `app-debug.apk` auf dem Xiaomi als Update installieren. **Nicht deinstallieren und keine App-Daten löschen**, wenn die vorhandene Konfiguration erhalten bleiben soll. Android Studio auf dem Mac ist dafür nicht nötig.

Alternativ kann ein bereits verbundenes ADB auf einem Computer die APK mit `adb -s XIAOMI_IP:5555 install -r app-debug.apk` installieren. Das ist für den normalen Betrieb nicht nötig.

## Einmalige Einrichtung

1. Auf dem Streamer ADB-Debugging aktivieren und beide Geräte im selben lokalen Netz lassen. Die Streamer-IP möglichst im Router reservieren.
2. Streamer Remote auf dem Xiaomi installieren, Streamer-IP und ADB-Port (standardmäßig 5555) eintragen und speichern.
3. Die Bedienungshilfe „Streamer Remote“ auf dem Xiaomi aktivieren. Beim ersten ADB-Befehl den Schlüssel der **Xiaomi-App** auf dem Streamer erlauben. Ein zuvor erlaubter Mac-Schlüssel genügt dafür nicht.
4. In Streamer Remote die App-Tasten-Umleitung einschalten. Die Ziel-Apps aus der vom Streamer abgefragten Liste wählen; Paketnamen müssen nicht eingetippt werden. Für die Apps-Taste kann „App-Übersicht“ gewählt werden.
5. Die Tasten auf dem Streamer-HDMI-Bildschirm testen. Die Diagnoseansichten der App zeigen empfangene Tastencodes und ADB-Ergebnisse.

Die aktuelle Verbindung nutzt klassisches ADB über TCP. Der Streamer-Port darf nicht am Router ins Internet freigegeben werden. Ein Google-TV-Pairing-Code-Verfahren ist nicht implementiert.

## Xiaomi-Menüfehler und Wiederherstellung

Der Xiaomi-Launcher zeigt beim normalen Öffnen der App gelegentlich „Fehler beim Laden des Menüs“. Im Xiaomi-Protokoll steht dabei `EntityMenuAssetFragment: Unable to load distributors` mit `net::ERR_CONNECTION_REFUSED`. Ein direkter Start von Streamer Remote funktioniert. Als auf dem Gerät getesteter Zugang öffnet **langes Apps auf der Xiaomi-Oberfläche** die Konfiguration direkt und umgeht dieses Xiaomi-Menü. Auch Xiaomi-Einstellungen → Apps → Streamer Remote → Öffnen funktioniert.

„Beenden erzwingen“ entfernt auf diesem Xiaomi die Bedienungshilfe aus den aktiven Diensten. Danach fängt die App keine Fernbedienungstasten mehr ab, obwohl der ADB-Schlüssel noch gespeichert ist. Die Bedienungshilfe muss in den Xiaomi-Einstellungen erneut eingeschaltet werden; die App zeigt ihren Status und öffnet die Bedienungshilfe-Einstellungen. Cache-Löschen ist zur Wiederherstellung der ADB-Freigabe nicht erforderlich.
