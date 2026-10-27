package fi.cheeseclues.mapautocontinue

import android.os.SystemClock

/** In-memory state shared by the foreground location service, Activity and accessibility service. */
object RideState {
    @Volatile var riding: Boolean = false
    @Volatile var speedKmh: Float? = null
    @Volatile var lastFixElapsedMs: Long = 0L
    @Volatile var movingSinceElapsedMs: Long = 0L
    @Volatile var lastAction: String = "Idle"
    @Volatile var accessibilityConnected: Boolean = false

    fun hasFreshMovingFix(thresholdKmh: Int): Boolean {
        val now = SystemClock.elapsedRealtime()
        val speed = speedKmh ?: return false
        return riding &&
            lastFixElapsedMs > 0L &&
            now - lastFixElapsedMs in 0L..5000L &&
            speed > thresholdKmh &&
            movingSinceElapsedMs > 0L &&
            now - movingSinceElapsedMs >= 2000L
    }

    fun reset() {
        riding = false
        speedKmh = null
        lastFixElapsedMs = 0L
        movingSinceElapsedMs = 0L
        lastAction = "Idle"
    }
}
