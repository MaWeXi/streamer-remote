package dev.local.streamerremote

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.flyfishxu.kadb.Kadb
import java.util.concurrent.Executors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Executes one command at a time, off the UI thread. No HTTP server or cloud service. */
object RemoteCommand {
    private val worker = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    fun home(context: Context) = execute(context, "input keyevent 3", diagnostic = "Home")
    fun testHome(context: Context) = execute(context, "input keyevent 3", notify = true)
    fun allApps(context: Context) = execute(context, "input keyevent 284")

    fun isValidPackage(packageName: String): Boolean =
        packageName.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)+"))

    fun launch(context: Context, packageName: String) {
        // A package name is a single safe token. Never interpolate arbitrary shell input.
        if (!isValidPackage(packageName)) return
        execute(context, "monkey -p $packageName -c android.intent.category.LEANBACK_LAUNCHER 1")
    }

    fun execute(context: Context, command: String, notify: Boolean = false, diagnostic: String? = null) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("remote", Context.MODE_PRIVATE)
        val host = prefs.getString("host", "")?.trim().orEmpty()
        val port = prefs.getInt("port", 5555)
        if (diagnostic != null) prefs.edit().putString("diag_adb", "$diagnostic: Befehl wartet auf ADB").apply()
        worker.execute {
            fun result(message: String) {
                if (diagnostic != null) {
                    val time = SimpleDateFormat("HH:mm:ss", Locale.GERMANY).format(Date())
                    prefs.edit().putString("diag_adb", "$diagnostic um $time: $message").apply()
                }
            }
            try {
                require(RemoteConfig.validIPv4(host) && port in 1..65535) { "IP-Adresse oder Port fehlt/ist ungültig" }
                Kadb.create(host, port).use { adb ->
                    val result = adb.shell(command)
                    if (result.exitCode != 0) error("ADB-Befehl fehlgeschlagen: ${result.output.take(160)}")
                    result("ADB-Befehl erfolgreich ausgeführt")
                    if (notify) ui.post { Toast.makeText(appContext, "ADB-Befehl erfolgreich gesendet", Toast.LENGTH_LONG).show() }
                }
            } catch (e: Exception) {
                result("ADB-Fehler: ${e.message}")
                ui.post { Toast.makeText(appContext, "ADB fehlgeschlagen: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }
}
