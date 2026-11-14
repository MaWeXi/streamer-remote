package dev.local.streamerremote

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent

class RemoteKeyService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var homeDown = false
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
        if (event.keyCode == RemoteConfig.code(prefs, "home")) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        homeDown = true
                        longHomeSent = false
                        handler.removeCallbacks(longHome)
                        handler.postDelayed(longHome, ViewConfiguration.getLongPressTimeout().toLong())
                    } else if (event.isLongPress && homeDown) {
                        handler.removeCallbacks(longHome)
                        longHome.run()
                    }
                    return longHomeSent
                }
                KeyEvent.ACTION_UP -> {
                    handler.removeCallbacks(longHome)
                    homeDown = false
                    val consumed = longHomeSent
                    longHomeSent = false
                    return consumed
                }
            }
            return false
        }
        val slot = RemoteConfig.defaultCodes.keys.firstOrNull {
            it != "home" && event.keyCode == RemoteConfig.code(prefs, it)
        } ?: return false
        val pkg = prefs.getString(slot, "").orEmpty()
        if (pkg.isBlank() || !RemoteCommand.isValidPackage(pkg)) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) RemoteCommand.launch(this, pkg)
        return true
    }
}
