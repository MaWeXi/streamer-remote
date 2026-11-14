package dev.local.streamerremote

import android.accessibilityservice.AccessibilityService
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
            RemoteCommand.home(this)
        }
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
            prefs.edit().putString("last_key", "${KeyEvent.keyCodeToString(event.keyCode)} (${event.keyCode})").apply()
        }
        if (event.keyCode == RemoteConfig.code(prefs, "home")) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
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
                    val wasDown = homeDown
                    homeDown = false
                    val wasLong = longHomeSent || SystemClock.uptimeMillis() - homeDownAt >= ViewConfiguration.getLongPressTimeout()
                    if (wasDown && !longHomeSent && wasLong) RemoteCommand.home(this)
                    if (wasDown && !wasLong) performGlobalAction(GLOBAL_ACTION_HOME)
                    longHomeSent = false
                    return true
                }
            }
            return false
        }
        val slot = RemoteConfig.defaultCodes.keys.firstOrNull {
            it != "home" && event.keyCode == RemoteConfig.code(prefs, it)
        } ?: return false
        val pkg = prefs.getString(slot, null)?.takeIf { it.isNotBlank() } ?: RemoteConfig.defaultPackages[slot].orEmpty()
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            if (slot == "apps" && pkg.isBlank()) RemoteCommand.allApps(this)
            else if (RemoteCommand.isValidPackage(pkg)) RemoteCommand.launch(this, pkg)
        }
        return true
    }
}
