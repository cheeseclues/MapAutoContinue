package fi.cheeseclues.mapautocontinue

import android.content.Context

object AppPrefs {
    private const val FILE = "settings"
    const val KEY_SPEED = "minimum_speed"

    fun minimumSpeed(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_SPEED, 8).coerceIn(5, 30)

    fun setMinimumSpeed(context: Context, speed: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt(KEY_SPEED, speed).apply()
    }
}
