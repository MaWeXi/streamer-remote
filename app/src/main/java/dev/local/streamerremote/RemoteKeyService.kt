package dev.local.streamerremote

import android.accessibilityservice.AccessibilityService
import android.content.Intent
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
    private var homeStartedOnTvInput = false
    private var returnToTvAfterShortHome = false
    private val returnToTv = Runnable {
        if (returnToTvAfterShortHome) {
            returnToTvAfterShortHome = false
            if (viewingTvInput) return@Runnable
            try {
                startActivity(Intent().setClassName("com.mitv.livetv",
                    "com.mitv.livetv.tv.MainActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                getSharedPreferences("remote", MODE_PRIVATE).edit()
                    .putString("diag_home_return", "Nach kurzem Home zur TV-Eingangsansicht zurückgekehrt").apply()
            } catch (error: Exception) {
                getSharedPreferences("remote", MODE_PRIVATE).edit()
                    .putString("diag_home_return", "Rücksprung fehlgeschlagen: ${error.message}").apply()
            }
        }
    }
    private var longHomeSent = false
    private var viewingTvInput = false
    private var appsDown = false
    private var appsDownAt = 0L
    private var appsLongHandled = false
    private val openConfigFromApps = Runnable {
        if (appsDown && !appsLongHandled) {
            appsLongHandled = true
            getSharedPreferences("remote", MODE_PRIVATE).edit()
                .putString("diag_action_apps", "Langes Apps: Streamer Remote direkt geöffnet").apply()
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    private val longHome = Runnable {
        if (homeDown && !longHomeSent) {
            longHomeSent = true
            getSharedPreferences("remote", MODE_PRIVATE).edit()
                .putString("diag_long", "Langer Home-Druck erkannt um ${stamp()}").apply()
            returnToTvAfterShortHome = false
            handler.removeCallbacks(returnToTv)
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun stamp(): String = SimpleDateFormat("HH:mm:ss", Locale.GERMANY).format(Date())

    override fun onServiceConnected() {
        super.onServiceConnected()
        viewingTvInput = getSharedPreferences("remote", MODE_PRIVATE)
            .getBoolean("viewing_tv_input", false)
        getSharedPreferences("remote", MODE_PRIVATE).edit()
            .putString("diag_service", "Aktiv seit ${stamp()}").apply()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        when (pkg) {
            "com.mitv.livetv" -> viewingTvInput = true
            "com.google.android.apps.tv.launcherx", packageName, "com.android.tv.settings" -> viewingTvInput = false
        }
        if (pkg == "com.google.android.apps.tv.launcherx" && returnToTvAfterShortHome) {
            handler.removeCallbacks(returnToTv)
            handler.postDelayed(returnToTv, 150L)
        }
        getSharedPreferences("remote", MODE_PRIVATE).edit()
            .putBoolean("viewing_tv_input", viewingTvInput)
            .putString("diag_apps_context", "Sichtbare Oberfläche: $pkg; TV-Eingang: $viewingTvInput").apply()
    }
    override fun onInterrupt() = Unit
    override fun onDestroy() {
        handler.removeCallbacks(longHome)
        handler.removeCallbacks(returnToTv)
        handler.removeCallbacks(openConfigFromApps)
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
        if (event.keyCode == KeyEvent.KEYCODE_TV_INPUT) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                try {
                    startActivity(Intent("com.android.tv.action.VIEW_INPUTS")
                        .setPackage("com.mitv.livetv")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    prefs.edit().putString("diag_input",
                        "Xiaomi-Eingangsmenü direkt geöffnet um ${stamp()}").apply()
                } catch (error: Exception) {
                    prefs.edit().putString("diag_input",
                        "Eingangsmenü fehlgeschlagen: ${error.message}").apply()
                    return false
                }
            }
            return true
        }
        if (event.keyCode == RemoteConfig.code(prefs, "home")) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        prefs.edit().putString("diag_down", "Home DOWN empfangen um ${stamp()} (Code ${event.keyCode})").apply()
                        homeDown = true
                        homeStartedOnTvInput = viewingTvInput
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
                        returnToTvAfterShortHome = false
                        handler.removeCallbacks(returnToTv)
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }
                    if (wasDown && !wasLong) {
                        prefs.edit().putString("diag_short", "Kurzer Home-Druck um ${stamp()}").apply()
                        if (homeStartedOnTvInput) {
                            returnToTvAfterShortHome = true
                            RemoteCommand.home(this)
                            handler.removeCallbacks(returnToTv)
                            handler.postDelayed(returnToTv, 900L)
                        } else performGlobalAction(GLOBAL_ACTION_HOME)
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
        if (slot == "apps" && !viewingTvInput) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        appsDown = true
                        appsDownAt = SystemClock.uptimeMillis()
                        appsLongHandled = false
                        prefs.edit().putString("diag_key_apps",
                            "Apps auf Xiaomi um ${stamp()} (Code ${event.keyCode})").apply()
                        handler.removeCallbacks(openConfigFromApps)
                        handler.postDelayed(openConfigFromApps,
                            ViewConfiguration.getLongPressTimeout().toLong())
                    }
                    return true
                }
                KeyEvent.ACTION_UP -> {
                    handler.removeCallbacks(openConfigFromApps)
                    val wasDown = appsDown
                    appsDown = false
                    if (wasDown && !appsLongHandled) {
                        val heldLong = SystemClock.uptimeMillis() - appsDownAt >=
                            ViewConfiguration.getLongPressTimeout()
                        if (heldLong) {
                            appsLongHandled = true
                            startActivity(Intent(this, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            prefs.edit().putString("diag_action_apps",
                                "Langes Apps: Streamer Remote direkt geöffnet").apply()
                        } else {
                            try {
                                startActivity(Intent(Intent.ACTION_ALL_APPS)
                                    .setPackage("com.google.android.apps.tv.launcherx")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                prefs.edit().putString("diag_action_apps",
                                    "Kurzes Apps: Xiaomi-App-Übersicht geöffnet").apply()
                            } catch (error: Exception) {
                                prefs.edit().putString("diag_action_apps",
                                    "Xiaomi-Apps fehlgeschlagen: ${error.message}").apply()
                            }
                        }
                    }
                    appsLongHandled = false
                    return true
                }
            }
            return true
        }
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
