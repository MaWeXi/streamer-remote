package dev.local.streamerremote

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.flyfishxu.kadb.Kadb
import java.util.concurrent.Executors

/** Executes one command at a time, off the UI thread. No HTTP server or cloud service. */
object RemoteCommand {
    private val worker = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    fun home(context: Context) = execute(context, "input keyevent 3")

    fun isValidPackage(packageName: String): Boolean =
        packageName.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)+"))

    fun launch(context: Context, packageName: String) {
        // A package name is a single safe token. Never interpolate arbitrary shell input.
        if (!isValidPackage(packageName)) return
        execute(context, "monkey -p $packageName -c android.intent.category.LAUNCHER 1")
    }

    fun execute(context: Context, command: String, notify: Boolean = false) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("remote", Context.MODE_PRIVATE)
        val host = prefs.getString("host", "")?.trim().orEmpty()
        val port = prefs.getInt("port", 5555)
        worker.execute {
            try {
                require(RemoteConfig.validIPv4(host) && port in 1..65535) { "IP-Adresse oder Port fehlt/ist ungültig" }
                Kadb.create(host, port).use { adb ->
                    val result = adb.shell(command)
                    if (notify) ui.post { Toast.makeText(appContext, "ADB: ${result.output.take(160)}", Toast.LENGTH_LONG).show() }
                }
            } catch (e: Exception) {
                ui.post { Toast.makeText(appContext, "ADB fehlgeschlagen: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }
}
