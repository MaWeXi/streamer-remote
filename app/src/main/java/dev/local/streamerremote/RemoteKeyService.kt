package dev.local.streamerremote

import android.accessibilityservice.AccessibilityService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent

class RemoteKeyService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var homeDown = false
    private var homeDownAt = 0L
    private var longHomeSent = false
    private val longHome = Runnable {
        if (homeDown && !longHomeSent) {
            longHomeSent = true
            getSharedPreferences("remote", MODE_PRIVATE).edit()
                .putString("diag_long", "Langer Home-Druck erkannt um ${stamp()}").apply()
            RemoteCommand.home(this)
        }
    }

    private fun stamp(): String = SimpleDateFormat("HH:mm:ss", Locale.GERMANY).format(Date())

    override fun onServiceConnected() {
        super.onServiceConnected()
        getSharedPreferences("remote", MODE_PRIVATE).edit()
            .putString("diag_service", "Aktiv seit ${stamp()}").apply()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    override fun onDestroy() {
        handler.removeCallbacks(longHome)
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val prefs = getSharedPreferences("remote", MODE_PRIVATE)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            if (event.keyCode !in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK)) {
                prefs.edit().putString("last_key", "${KeyEvent.keyCodeToString(event.keyCode)} (${event.keyCode}) um ${stamp()}").apply()
            }
        }
        if (event.keyCode == RemoteConfig.code(prefs, "home")) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        prefs.edit().putString("diag_down", "Home DOWN empfangen um ${stamp()} (Code ${event.keyCode})").apply()
                        homeDown = true
                        homeDownAt = SystemClock.uptimeMillis()
                        longHomeSent = false
                        handler.removeCallbacks(longHome)
                        handler.postDelayed(longHome, ViewConfiguration.getLongPressTimeout().toLong())
                    } else if (event.isLongPress && homeDown) {
                        handler.removeCallbacks(longHome)
                        longHome.run()
                    }
                    return true
                }
                KeyEvent.ACTION_UP -> {
                    handler.removeCallbacks(longHome)
                    prefs.edit().putString("diag_up", "Home UP empfangen um ${stamp()}").apply()
                    val wasDown = homeDown
                    homeDown = false
                    val wasLong = longHomeSent || SystemClock.uptimeMillis() - homeDownAt >= ViewConfiguration.getLongPressTimeout()
                    if (wasDown && !longHomeSent && wasLong) {
                        prefs.edit().putString("diag_long", "Langer Home-Druck beim Loslassen erkannt um ${stamp()}").apply()
                        RemoteCommand.home(this)
                    }
                    if (wasDown && !wasLong) {
                        prefs.edit().putString("diag_short", "Kurzer Home-Druck um ${stamp()}").apply()
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }
                    longHomeSent = false
                    return true
                }
            }
            return false
        }
        val slot = RemoteConfig.defaultCodes.keys.firstOrNull {
            it != "home" && event.keyCode == RemoteConfig.code(prefs, it)
        } ?: return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            prefs.edit().putString("diag_key_$slot",
                "$slot empfangen um ${stamp()} (Code ${event.keyCode})").apply()
            if (!prefs.getBoolean("enable_app_keys", false)) {
                prefs.edit().putString("diag_action_$slot", "Umleitung ausgeschaltet").apply()
                return false
            }
            val pkg = prefs.getString(slot, null)?.takeIf { it.isNotBlank() }
                ?: RemoteConfig.defaultPackages[slot].orEmpty()
            if (slot == "apps" && pkg.isBlank()) {
                RemoteCommand.allApps(this, slot)
            } else if (RemoteCommand.isValidPackage(pkg)) {
                RemoteCommand.launch(this, pkg, slot)
            } else {
                prefs.edit().putString("diag_action_$slot", "Keine gültige App gewählt").apply()
            }
        }
        return prefs.getBoolean("enable_app_keys", false)
    }
}
