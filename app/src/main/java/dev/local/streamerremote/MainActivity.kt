package dev.local.streamerremote

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.text.InputType
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        title("Streamer Remote — Home-Diagnose 0.6")
        val host = field("IP-Adresse des Google TV Streamers", prefs.getString("host", "").orEmpty())
        val port = field("ADB-Port", prefs.getInt("port", 5555).toString(), numeric = true)
        val names = listOf("netflix" to "Netflix", "prime" to "Prime Video", "youtube" to "YouTube", "apps" to "All Apps")
        val codeFields = RemoteConfig.defaultCodes.mapValues { (slot, default) ->
            field("Tastencode: $slot", prefs.getInt("code_$slot", default).toString(), numeric = true)
        }
        val mapping = names.associate { (slot, label) ->
            val hint = if (slot == "apps") "leer = App-Übersicht" else "leer = Standard-App"
            slot to field("$label → Paketname auf dem Streamer ($hint)", prefs.getString(slot, "").orEmpty())
        }
        button("Einstellungen speichern") {
            val parsedPort = port.text.toString().toIntOrNull()
            if (parsedPort == null || parsedPort !in 1..65535) { Toast.makeText(this, "Ungültiger Port", Toast.LENGTH_SHORT).show(); return@button }
            if (!RemoteConfig.validIPv4(host.text.toString().trim())) { Toast.makeText(this, "Ungültige IPv4-Adresse", Toast.LENGTH_SHORT).show(); return@button }
            val codes = codeFields.mapValues { (_, edit) -> edit.text.toString().toIntOrNull() }
            if (codes.values.any { it == null || !RemoteConfig.validCode(it) } || codes.values.distinct().size != codes.size) {
                Toast.makeText(this, "Tastencodes müssen gültig und verschieden sein", Toast.LENGTH_LONG).show(); return@button
            }
            if (mapping.values.any { it.text.toString().trim().isNotBlank() && !RemoteCommand.isValidPackage(it.text.toString().trim()) }) { Toast.makeText(this, "Ungültiger Paketname", Toast.LENGTH_SHORT).show(); return@button }
            prefs.edit().putString("host", host.text.toString().trim()).putInt("port", parsedPort).apply {
                mapping.forEach { (slot, edit) -> putString(slot, edit.text.toString().trim()) }
                codes.forEach { (slot, code) -> putInt("code_$slot", code!!) }
            }.apply()
            Toast.makeText(this, "Gespeichert", Toast.LENGTH_SHORT).show()
        }
        title("Home-Diagnose")
        val diagnostic = TextView(this).apply { textSize = 18f; setPadding(0, 8, 0, 8) }
        root.addView(diagnostic)
        fun refreshDiagnostic() {
            val lines = listOf("diag_service", "diag_down", "diag_up", "diag_long", "diag_short", "diag_adb")
                .map { prefs.getString(it, null) ?: when (it) {
                    "diag_service" -> "Bedienungshilfe: kein Start protokolliert"
                    "diag_down" -> "Home DOWN: nicht empfangen"
                    "diag_up" -> "Home UP: nicht empfangen"
                    "diag_long" -> "Langer Home-Druck: nicht erkannt"
                    "diag_short" -> "Kurzer Home-Druck: nicht erkannt"
                    else -> "ADB durch Home-Taste: kein Befehl"
                } }
            diagnostic.text = lines.joinToString("\n")
        }
        refreshDiagnostic()
        button("Home-Diagnose zurücksetzen") {
            prefs.edit().remove("diag_down").remove("diag_up").remove("diag_long")
                .remove("diag_short").remove("diag_adb").apply()
            refreshDiagnostic()
        }
        button("Home-Diagnose aktualisieren") { refreshDiagnostic() }
        button("Bedienungshilfen öffnen") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        title("Home: kurzer Druck bleibt Xiaomi. Langer Druck: Streamer Home (experimentell).")
        title("Wichtig: Vorher Button Mapper als Tastenfilter deaktivieren. Nur eine Accessibility-App kann zuverlässig Key-Events filtern.")
        title("ADB muss auf dem Streamer eingeschaltet sein. Beim ersten Verbindungsversuch ggf. ADB-Schlüssel am Streamer autorisieren.")
    }
}
