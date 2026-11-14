package dev.local.streamerremote

import android.app.Application
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.OkioFilePrivateKeyStore
import okio.Path.Companion.toPath
import java.io.File

class RemoteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // filesDir survives process restarts and "clear cache"; cacheDir does not.
        val keyPath = File(filesDir, "adb_host_key.pem").absolutePath.toPath()
        KadbCert.configure(OkioFilePrivateKeyStore(keyPath))
    }
}
