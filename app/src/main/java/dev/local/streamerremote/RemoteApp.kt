package dev.local.streamerremote

import android.app.Application
import android.util.Log
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.OkioFilePrivateKeyStore
import okio.Path.Companion.toPath
import java.io.File

class RemoteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // filesDir survives process restarts and "clear cache"; cacheDir does not.
        try {
            val keyPath = File(filesDir, "adb_host_key.pem").absolutePath.toPath()
            KadbCert.configure(OkioFilePrivateKeyStore(keyPath))
            getSharedPreferences("remote", MODE_PRIVATE).edit().remove("diag_app_error").apply()
        } catch (error: Throwable) {
            Log.e("StreamerRemote", "ADB identity setup failed", error)
            getSharedPreferences("remote", MODE_PRIVATE).edit()
                .putString("diag_app_error", error.stackTraceToString().take(1000)).apply()
        }
    }
}
