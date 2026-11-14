package dev.local.streamerremote

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.text.InputType
import android.widget.*
import android.util.Log

class MainActivity : Activity() {
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

        title("Streamer Remote — Home-Diagnose 0.10")
        val host = field("IP-Adresse des Google TV Streamers", prefs.getString("host", "").orEmpty())
        val port = field("ADB-Port", prefs.getInt("port", 5555).toString(), numeric = true)
        val homeCode = field("Tastencode Home", prefs.getInt("code_home", 3).toString(), numeric = true)
        button("Einstellungen speichern") {
            val parsedPort = port.text.toString().toIntOrNull()
            if (parsedPort == null || parsedPort !in 1..65535) { Toast.makeText(this, "Ungültiger Port", Toast.LENGTH_SHORT).show(); return@button }
            if (!RemoteConfig.validIPv4(host.text.toString().trim())) { Toast.makeText(this, "Ungültige IPv4-Adresse", Toast.LENGTH_SHORT).show(); return@button }
            val code = homeCode.text.toString().toIntOrNull()
            if (code == null || !RemoteConfig.validCode(code)) {
                Toast.makeText(this, "Ungültiger Home-Tastencode", Toast.LENGTH_SHORT).show(); return@button
            }
            prefs.edit().putString("host", host.text.toString().trim()).putInt("port", parsedPort).apply {
                putInt("code_home", code)
            }.apply()
            Toast.makeText(this, "Gespeichert", Toast.LENGTH_SHORT).show()
        }
        title("Home-Diagnose")
        prefs.getString("diag_app_error", null)?.let { title("Startfehler: $it") }
        val diagnostic = TextView(this).apply { textSize = 18f; setPadding(0, 8, 0, 8) }
        root.addView(diagnostic)
        fun refreshDiagnostic() {
            val lines = listOf("diag_service", "diag_down", "diag_up", "diag_long", "diag_short", "diag_adb", "last_key")
                .map { prefs.getString(it, null) ?: when (it) {
                    "diag_service" -> "Bedienungshilfe: kein Start protokolliert"
                    "diag_down" -> "Home DOWN: nicht empfangen"
                    "diag_up" -> "Home UP: nicht empfangen"
                    "diag_long" -> "Langer Home-Druck: nicht erkannt"
                    "diag_short" -> "Kurzer Home-Druck: nicht erkannt"
                    "diag_adb" -> "ADB durch Home-Taste: kein Befehl"
                    else -> "Letzte andere Taste: keine"
                } }
            diagnostic.text = lines.joinToString("\n")
        }
        refreshDiagnostic()
        button("Home-Diagnose zurücksetzen") {
            prefs.edit().remove("diag_down").remove("diag_up").remove("diag_long")
                .remove("diag_short").remove("diag_adb").remove("last_key").apply()
            refreshDiagnostic()
        }
        button("Home-Diagnose aktualisieren") { refreshDiagnostic() }
        button("Bedienungshilfen öffnen") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        title("Nur Home ist aktiv: kurz = Xiaomi-Startseite, lang = Streamer-Startseite.")
        title("Wichtig: Vorher Button Mapper als Tastenfilter deaktivieren. Nur eine Accessibility-App kann zuverlässig Key-Events filtern.")
        title("ADB muss auf dem Streamer eingeschaltet sein. Beim ersten Verbindungsversuch ggf. ADB-Schlüssel am Streamer autorisieren.")
    }
}
