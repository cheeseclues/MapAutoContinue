package fi.cheeseclues.mapautocontinue

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/** Runs location updates as a user-started foreground service while Google Maps is visible. */
class RideService : Service() {
    companion object {
        const val ACTION_START = "fi.cheeseclues.mapautocontinue.START"
        const val ACTION_STOP = "fi.cheeseclues.mapautocontinue.STOP"
        private const val CHANNEL_ID = "ride_status"
        private const val NOTIFICATION_ID = 42
        private const val TAG = "MapAutoContinue"
    }

    private var lastLocationLogMs = 0L
    private var lastLocationSummary = ""

    private val locationManager by lazy { getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    private val locationListener = LocationListener { location -> onLocation(location) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) return START_NOT_STICKY
        if (RideState.riding) return START_NOT_STICKY

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            RideState.lastAction = "Precise location permission missing"
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            createNotificationChannel()
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            RideState.reset()
            RideState.riding = true
            RideState.lastAction = "Ride started. Waiting for GPS and Google Maps."
            Log.i(TAG, "Ride monitoring STARTED; awaiting GPS updates")
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0f,
                locationListener,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start GPS foreground service", e)
            stopSelf()
            RideState.lastAction = "GPS could not start: ${e.javaClass.simpleName}"
        }
        return START_NOT_STICKY
    }

    private fun onLocation(location: Location) {
        // A poor or stale fix must NEVER trigger an automatic tap.
        val now = SystemClock.elapsedRealtime()
        val fixTimeMs = location.elapsedRealtimeNanos / 1_000_000L
        val usable = location.hasSpeed() &&
            location.hasAccuracy() && location.accuracy <= 50f &&
            fixTimeMs in (now - 5000L)..now

        if (!usable) {
            val why = when {
                !location.hasSpeed() -> "GPS update missing speed (simulator may be stationary)"
                !location.hasAccuracy() -> "GPS update missing accuracy"
                location.accuracy > 50f -> "GPS accuracy worse than 50m"
                else -> "GPS update stale"
            }
            logLocationStatus(why)
            RideState.speedKmh = null
            RideState.movingSinceElapsedMs = 0L
            return
        }

        val speed = location.speed.coerceAtLeast(0f) * 3.6f
        RideState.lastFixElapsedMs = fixTimeMs
        RideState.speedKmh = speed
        logLocationStatus("GPS speed %.1f km/h (threshold %d km/h)".format(
            java.util.Locale.US, speed, AppPrefs.minimumSpeed(this)
        ), repeatOnTime = false)
        if (speed > AppPrefs.minimumSpeed(this)) {
            if (RideState.movingSinceElapsedMs == 0L) RideState.movingSinceElapsedMs = now
        } else {
            RideState.movingSinceElapsedMs = 0L
        }
    }

    private fun logLocationStatus(message: String, repeatOnTime: Boolean = true) {
        val now = SystemClock.elapsedRealtime()
        val category = if (message.startsWith("GPS speed")) "valid speed" else message
        if (category != lastLocationSummary || (repeatOnTime && now - lastLocationLogMs >= 15000L)) {
            Log.i(TAG, message)
            lastLocationSummary = category
            lastLocationLogMs = now
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Active ride",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shows when Google Maps auto-continue monitoring is running" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, RideService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Maps Auto-Continue active")
            .setContentText("Auto-continue on · tap to open")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(
                android.R.drawable.ic_media_pause, "Stop ride", stopIntent
            ).build())
            .build()
    }

    override fun onDestroy() {
        try {
            locationManager.removeUpdates(locationListener)
        } catch (_: Exception) { /* Never leave a running listener on shutdown. */ }
        RideState.reset()
        Log.i(TAG, "Ride monitoring STOPPED")
        super.onDestroy()
    }
}
