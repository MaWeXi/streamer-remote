package dev.local.streamerremote

import android.content.SharedPreferences
import android.view.KeyEvent

object RemoteConfig {
    val defaultCodes = linkedMapOf("home" to 3, "netflix" to 193, "prime" to 194, "youtube" to 190, "apps" to 284)

    fun code(prefs: SharedPreferences, slot: String): Int =
        prefs.getInt("code_$slot", defaultCodes.getValue(slot))

    fun validIPv4(host: String): Boolean {
        val parts = host.split('.')
        return parts.size == 4 && parts.all { part ->
            part.isNotEmpty() && part.length <= 3 && part.all(Char::isDigit) &&
                part.toIntOrNull() in 0..255
        }
    }

    fun validCode(code: Int): Boolean = code in 1..KeyEvent.getMaxKeyCode()
}
