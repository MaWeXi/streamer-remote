package dev.local.streamerremote

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.ViewGroup
import android.text.InputType
import android.widget.*
import android.util.Log

class MainActivity : Activity() {
    private var micDiagnosticText: TextView? = null

    private fun startMicrophoneDiagnostic() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 7101)
            micDiagnosticText?.text = "Mikrofonberechtigung wird angefragt."
            return
        }
        micDiagnosticText?.text = "Aufnahme läuft 6 Sekunden. Sprich in die Xiaomi-Fernbedienung und halte dabei ihre Mikrofontaste."
        Thread {
            var recorder: AudioRecord? = null
            try {
                val sampleRate = 16000
                val minBytes = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT)
                check(minBytes > 0) { "Audioeingang nicht verfügbar (Code $minBytes)" }
                recorder = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, maxOf(minBytes, sampleRate))
                check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Audioeingang ließ sich nicht öffnen" }
                recorder.startRecording()
                val buffer = ShortArray(1024)
                var frames = 0L
                var squares = 0.0
                var peak = 0
                val deadline = System.currentTimeMillis() + 6000L
                while (System.currentTimeMillis() < deadline) {
                    val count = recorder.read(buffer, 0, buffer.size)
                    if (count > 0) for (i in 0 until count) {
                        val sample = buffer[i].toInt()
                        peak = maxOf(peak, kotlin.math.abs(sample))
                        squares += sample.toDouble() * sample
                        frames++
                    }
                }
                val rms = if (frames == 0L) 0.0 else kotlin.math.sqrt(squares / frames)
                runOnUiThread {
                    val summary = "Aufnahme beendet. Spitzenpegel: " + peak +
                        " / 32767; Durchschnitt: " + "%.0f".format(rms) + ". " +
                        if (peak > 400) "Audio kam an." else "Kein messbarer Ton am App-Audioeingang."
                    getSharedPreferences("remote", Context.MODE_PRIVATE).edit()
                        .putString("diag_mic", summary).apply()
                    micDiagnosticText?.text = summary
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    val summary = "Mikrofontest fehlgeschlagen: " + error.message
                    getSharedPreferences("remote", Context.MODE_PRIVATE).edit()
                        .putString("diag_mic", summary).apply()
                    micDiagnosticText?.text = summary
                }
            } finally {
                try { recorder?.stop() } catch (_: Exception) {}
                recorder?.release()
            }
        }.start()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7101) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startMicrophoneDiagnostic()
            else micDiagnosticText?.text = "Mikrofonberechtigung wurde abgelehnt."
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            showScreen()
        } catch (error: Throwable) {
            Log.e("StreamerRemote", "Main screen failed", error)
            val message = error.stackTraceToString().take(3000)
            getSharedPreferences("remote", Context.MODE_PRIVATE).edit()
                .putString("diag_ui_error", message).apply()
            setContentView(ScrollView(this).apply {
                addView(TextView(this@MainActivity).apply {
                    text = "Streamer Remote konnte nicht geöffnet werden:\n\n$message"
                    textSize = 18f
                    setPadding(32, 32, 32, 32)
                })
            })
        }
    }

    private fun showScreen() {
        val prefs = getSharedPreferences("remote", Context.MODE_PRIVATE)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(36, 24, 36, 24) }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)

        fun title(s: String) { root.addView(TextView(this).apply { text = s; textSize = 20f; setPadding(0, 8, 0, 8) }) }
        fun field(label: String, value: String, numeric: Boolean = false): EditText {
            root.addView(TextView(this).apply { text = label })
            return EditText(this).apply { setSingleLine(true); if (numeric) inputType = InputType.TYPE_CLASS_NUMBER; setText(value); root.addView(this, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) }
        }
        fun button(label: String, action: () -> Unit) {
            root.addView(Button(this).apply { text = label; setOnClickListener { action() } })
        }

        title("Streamer Remote — Streamer Remote 0.21")
        val host = field("IP-Adresse des Google TV Streamers", prefs.getString("host", "").orEmpty())
        val port = field("ADB-Port", prefs.getInt("port", 5555).toString(), numeric = true)
        val homeCode = field("Tastencode Home", prefs.getInt("code_home", 3).toString(), numeric = true)
        val slots = listOf("netflix" to "Netflix-Taste", "prime" to "Prime-Video-Taste",
            "youtube" to "YouTube-Taste", "apps" to "Apps-Taste")
        val keyCodes = slots.associate { (slot, label) ->
            slot to field("Tastencode $label", prefs.getInt("code_$slot",
                RemoteConfig.defaultCodes.getValue(slot)).toString(), numeric = true)
        }
        val selectedApps = slots.associate { (slot, _) ->
            slot to (prefs.getString(slot, null) ?: RemoteConfig.defaultPackages[slot].orEmpty())
        }.toMutableMap()
        val appKeysEnabled = CheckBox(this).apply {
            text = "App-Tasten zum Streamer umleiten (Test)"
            isChecked = prefs.getBoolean("enable_app_keys", false)
            setOnCheckedChangeListener { _, enabled ->
                prefs.edit().putBoolean("enable_app_keys", enabled).apply()
            }
        }
        root.addView(appKeysEnabled)
        title("Apps auf dem Streamer auswählen")
        slots.forEach { (slot, label) ->
            val choice = Button(this)
            fun updateChoice() {
                val pkg = selectedApps[slot].orEmpty()
                choice.text = "$label: " + if (pkg.isBlank()) "App-Übersicht" else AppCatalog.label(pkg)
            }
            updateChoice()
            choice.setOnClickListener {
                val currentHost = host.text.toString().trim()
                val currentPort = port.text.toString().toIntOrNull()
                if (!RemoteConfig.validIPv4(currentHost) || currentPort == null || currentPort !in 1..65535) {
                    Toast.makeText(this, "Bitte zuerst Streamer-IP und Port eingeben", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                prefs.edit().putString("host", currentHost).putInt("port", currentPort).apply()
                Toast.makeText(this, "Lese Apps vom Streamer…", Toast.LENGTH_SHORT).show()
                RemoteCommand.listApps(this) { packages, error ->
                    if (isFinishing || isDestroyed) return@listApps
                    if (error != null || packages == null) {
                        Toast.makeText(this, error ?: "App-Liste nicht verfügbar", Toast.LENGTH_LONG).show()
                    } else {
                        val options = if (slot == "apps") listOf("") + packages else packages
                        val labels = options.map { pkg ->
                            if (pkg.isBlank()) "App-Übersicht" else "${AppCatalog.label(pkg)} ($pkg)"
                        }.toTypedArray()
                        AlertDialog.Builder(this).setTitle("$label: App wählen")
                            .setItems(labels) { _, index ->
                                val pkg = options[index]
                                selectedApps[slot] = pkg
                                prefs.edit().putString(slot, pkg).apply()
                                updateChoice()
                            }.show()
                    }
                }
            }
            root.addView(choice)
        }
        button("Einstellungen speichern") {
            val parsedPort = port.text.toString().toIntOrNull()
            if (parsedPort == null || parsedPort !in 1..65535) { Toast.makeText(this, "Ungültiger Port", Toast.LENGTH_SHORT).show(); return@button }
            if (!RemoteConfig.validIPv4(host.text.toString().trim())) { Toast.makeText(this, "Ungültige IPv4-Adresse", Toast.LENGTH_SHORT).show(); return@button }
            val code = homeCode.text.toString().toIntOrNull()
            if (code == null || !RemoteConfig.validCode(code)) {
                Toast.makeText(this, "Ungültiger Home-Tastencode", Toast.LENGTH_SHORT).show(); return@button
            }
            val appCodes = keyCodes.mapValues { (_, edit) -> edit.text.toString().toIntOrNull() }
            val allCodes = listOf(code) + appCodes.values.filterNotNull()
            if (appCodes.values.any { it == null || !RemoteConfig.validCode(it) } || allCodes.distinct().size != 5) {
                Toast.makeText(this, "Tastencodes müssen gültig und verschieden sein", Toast.LENGTH_LONG).show(); return@button
            }
            prefs.edit().putString("host", host.text.toString().trim()).putInt("port", parsedPort).apply {
                putInt("code_home", code)
                appCodes.forEach { (slot, value) -> putInt("code_$slot", value!!) }
            }.apply()
            Toast.makeText(this, "Gespeichert", Toast.LENGTH_SHORT).show()
        }
        title("Tasten- und Mikrofon-Diagnose")
        val navigationDiagnostic = TextView(this).apply { textSize = 16f; setPadding(0, 8, 0, 8) }
        root.addView(navigationDiagnostic)
        fun refreshNavigationDiagnostic() {
            navigationDiagnostic.text = (prefs.getString("diag_navigation_action", null)?.plus("\\n") ?: "") +
                (prefs.getString("diag_navigation", null)
                    ?: "Zurück, Pfeiltasten und Mikrofon: noch keine Signale empfangen")
        }
        refreshNavigationDiagnostic()
        button("Tastensignale aktualisieren") { refreshNavigationDiagnostic() }
        button("Tastensignale zurücksetzen") {
            prefs.edit().remove("diag_navigation").apply()
            refreshNavigationDiagnostic()
        }
        micDiagnosticText = TextView(this).apply {
            textSize = 16f
            setPadding(0, 8, 0, 8)
            text = prefs.getString("diag_mic", "Noch kein Mikrofontest durchgeführt.")
        }
        root.addView(micDiagnosticText)
        button("Mikrofon der Fernbedienung prüfen") { startMicrophoneDiagnostic() }
        button("Mikrofontest-Ergebnis zurücksetzen") {
            prefs.edit().remove("diag_mic").apply()
            micDiagnosticText?.text = "Noch kein Mikrofontest durchgeführt."
        }

        title("Home-Diagnose")
        prefs.getString("diag_app_error", null)?.let { title("Startfehler: $it") }
        val diagnostic = TextView(this).apply { textSize = 18f; setPadding(0, 8, 0, 8) }
        root.addView(diagnostic)
        fun refreshDiagnostic() {
            val lines = listOf("diag_service", "diag_down", "diag_up", "diag_long", "diag_short", "diag_adb", "diag_home_return", "diag_input", "last_key")
                .map { prefs.getString(it, null) ?: when (it) {
                    "diag_service" -> "Bedienungshilfe: kein Start protokolliert"
                    "diag_down" -> "Home DOWN: nicht empfangen"
                    "diag_up" -> "Home UP: nicht empfangen"
                    "diag_long" -> "Langer Home-Druck: nicht erkannt"
                    "diag_short" -> "Kurzer Home-Druck: nicht erkannt"
                    "diag_adb" -> "ADB durch Home-Taste: kein Befehl"
                    "diag_home_return" -> "HDMI-Rücksprung nach kurzem Home: noch nicht benötigt"
                    "diag_input" -> "Eingangstaste: noch nicht gedrückt"
                    else -> "Letzte andere Taste: keine"
                } }
            diagnostic.text = lines.joinToString("\n")
        }
        refreshDiagnostic()
        button("Home-Diagnose zurücksetzen") {
            prefs.edit().remove("diag_down").remove("diag_up").remove("diag_long")
                .remove("diag_short").remove("diag_adb").remove("diag_home_return").remove("diag_input").remove("last_key").apply()
            refreshDiagnostic()
        }
        button("Home-Diagnose aktualisieren") { refreshDiagnostic() }
        title("App-Tasten-Diagnose")
        val appDiagnostic = TextView(this).apply { textSize = 17f; setPadding(0, 8, 0, 8) }
        root.addView(appDiagnostic)
        fun refreshAppDiagnostic() {
            val context = prefs.getString("diag_apps_context", null) ?: "Sichtbare Oberfläche: noch nicht erkannt"
            appDiagnostic.text = context + "\n\n" + slots.joinToString("\n\n") { (slot, label) ->
                val received = prefs.getString("diag_key_$slot", null) ?: "kein Tastendruck empfangen"
                val action = prefs.getString("diag_action_$slot", null) ?: "kein ADB-Befehl"
                "$label: $received\n$action"
            }
        }
        refreshAppDiagnostic()
        button("App-Tasten-Diagnose aktualisieren") { refreshAppDiagnostic() }
        button("App-Tasten-Diagnose zurücksetzen") {
            prefs.edit().apply {
                slots.forEach { (slot, _) ->
                    remove("diag_key_$slot")
                    remove("diag_action_$slot")
                }
            }.apply()
            refreshAppDiagnostic()
        }
        val serviceStatus = TextView(this).apply { textSize = 18f }
        root.addView(serviceStatus)
        fun refreshServiceStatus() {
            val enabled = Settings.Secure.getString(contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
                .contains("dev.local.streamerremote/dev.local.streamerremote.RemoteKeyService")
            serviceStatus.text = if (enabled) "Bedienungshilfe: aktiv" else
                "Bedienungshilfe: aus. Nach „Beenden erzwingen“ auf Xiaomi erneut einschalten."
        }
        refreshServiceStatus()
        button("Bedienungshilfe-Status aktualisieren") { refreshServiceStatus() }
        button("Bedienungshilfen öffnen") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        title("Home: kurz auf HDMI = Streamer-Startseite, kurz auf Xiaomi = Xiaomi-Startseite; lang = Xiaomi-Startseite.")
        title("Apps: auf dem Streamer dessen App-Übersicht, auf Xiaomi dessen Apps. Apps auf Xiaomi lange halten öffnet diese Konfiguration direkt.")
        title("Eingangstaste: öffnet direkt die Xiaomi-Eingangsauswahl.")
        title("Wichtig: Vorher Button Mapper als Tastenfilter deaktivieren. Nur eine Accessibility-App kann zuverlässig Key-Events filtern.")
        title("ADB muss auf dem Streamer eingeschaltet sein. Beim ersten Verbindungsversuch ggf. ADB-Schlüssel am Streamer autorisieren.")
    }
}
