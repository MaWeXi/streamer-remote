package dev.local.streamerremote

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import com.flyfishxu.kadb.Kadb
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Executes one command at a time, off the UI thread. No HTTP server or cloud service. */
object RemoteCommand {
    private val worker = Executors.newSingleThreadExecutor()
    private val repeatWorker = Executors.newCachedThreadPool()
    private val activeRepeaters = ConcurrentHashMap<Int, AtomicBoolean>()
    private val ui = Handler(Looper.getMainLooper())

    fun home(context: Context) = execute(context, "input keyevent 3", diagnostic = "Home")
    fun testHome(context: Context) = execute(context, "input keyevent 3", notify = true)
    fun allApps(context: Context, diagnostic: String? = null) = execute(context, "input keyevent 284", diagnostic = diagnostic)

    fun startAcceleratedKey(context: Context, keyCode: Int) {
        if (keyCode !in setOf(19, 20, 21, 22) || activeRepeaters.containsKey(keyCode)) return
        val running = AtomicBoolean(true)
        if (activeRepeaters.putIfAbsent(keyCode, running) != null) return
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("remote", Context.MODE_PRIVATE)
        val host = prefs.getString("host", "")?.trim().orEmpty()
        val port = prefs.getInt("port", 5555)
        val startedAt = SystemClock.uptimeMillis()
        prefs.edit().putString("diag_navigation_action", "Beschleunigte ADB-Wiederholung gestartet: Keycode $keyCode").apply()
        repeatWorker.execute {
            try {
                require(RemoteConfig.validIPv4(host) && port in 1..65535) { "Streamer-IP/Port ungültig" }
                Kadb.create(host, port).use { adb ->
                    val first = adb.shell("input keyevent $keyCode")
                    if (first.exitCode != 0) error(first.output.take(160))
                    while (running.get()) {
                        val heldMs = SystemClock.uptimeMillis() - startedAt
                        val delay = when {
                            heldMs < 700L -> 280L
                            heldMs < 1500L -> 200L
                            heldMs < 2500L -> 140L
                            else -> 100L
                        }
                        Thread.sleep(delay)
                        if (!running.get()) break
                        val repeated = adb.shell("input keyevent --longpress $keyCode")
                        if (repeated.exitCode != 0) error(repeated.output.take(160))
                    }
                }
            } catch (error: Exception) {
                prefs.edit().putString("diag_navigation_action", "ADB-Tastenwiederholung fehlgeschlagen: ${error.message}").apply()
                ui.post { Toast.makeText(appContext, "ADB-Taste fehlgeschlagen: ${error.message}", Toast.LENGTH_LONG).show() }
            } finally {
                activeRepeaters.remove(keyCode, running)
            }
        }
    }

    fun stopAcceleratedKey(keyCode: Int) {
        activeRepeaters[keyCode]?.set(false)
    }

    fun isValidPackage(packageName: String): Boolean =
        packageName.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)+"))

    fun launch(context: Context, packageName: String, diagnostic: String? = null) {
        // A package name is a single safe token. Never interpolate arbitrary shell input.
        if (!isValidPackage(packageName)) return
        execute(context, "monkey -p $packageName -c android.intent.category.LEANBACK_LAUNCHER 1", diagnostic = diagnostic)
    }

    fun listApps(context: Context, callback: (List<String>?, String?) -> Unit) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("remote", Context.MODE_PRIVATE)
        val host = prefs.getString("host", "")?.trim().orEmpty()
        val port = prefs.getInt("port", 5555)
        worker.execute {
            try {
                require(RemoteConfig.validIPv4(host) && port in 1..65535) { "Bitte IP-Adresse speichern" }
                Kadb.create(host, port).use { adb ->
                    val thirdParty = adb.shell("pm list packages -3")
                    val all = adb.shell("pm list packages")
                    if (thirdParty.exitCode != 0 || all.exitCode != 0) error("App-Liste konnte nicht gelesen werden")
                    fun parse(output: String): Set<String> = output.lineSequence()
                        .map { it.trim().removePrefix("package:") }
                        .filter(RemoteCommand::isValidPackage).toSet()
                    val installed = parse(all.output)
                    val candidates = parse(thirdParty.output) + AppCatalog.labels.keys.filter { it in installed }
                    val sorted = candidates.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { AppCatalog.label(it) })
                    ui.post { callback(sorted, null) }
                }
            } catch (error: Exception) {
                ui.post { callback(null, error.message ?: "ADB-Verbindung fehlgeschlagen") }
            }
        }
    }

    fun execute(context: Context, command: String, notify: Boolean = false, diagnostic: String? = null) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("remote", Context.MODE_PRIVATE)
        val host = prefs.getString("host", "")?.trim().orEmpty()
        val port = prefs.getInt("port", 5555)
        val diagnosticKey = if (diagnostic == "Home") "diag_adb" else "diag_action_$diagnostic"
        if (diagnostic != null) prefs.edit().putString(diagnosticKey, "$diagnostic: Befehl wartet auf ADB").apply()
        worker.execute {
            fun result(message: String) {
                if (diagnostic != null) {
                    val time = SimpleDateFormat("HH:mm:ss", Locale.GERMANY).format(Date())
                    prefs.edit().putString(diagnosticKey, "$diagnostic um $time: $message").apply()
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
