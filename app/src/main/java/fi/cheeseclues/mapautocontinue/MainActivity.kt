package fi.cheeseclues.mapautocontinue

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var txtRide: TextView
    private lateinit var txtAccessibility: TextView
    private lateinit var txtSpeed: TextView
    private lateinit var txtLastAction: TextView
    private lateinit var txtThreshold: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private val handler = Handler(Looper.getMainLooper())
    private val updateUi = object : Runnable {
        override fun run() {
            refreshUi()
            handler.postDelayed(this, 1000L)
        }
    }

    companion object { private const val PERMISSION_REQUEST = 1001 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtRide = findViewById(R.id.txtRide)
        txtAccessibility = findViewById(R.id.txtAccessibility)
        txtSpeed = findViewById(R.id.txtSpeed)
        txtLastAction = findViewById(R.id.txtLastAction)
        txtThreshold = findViewById(R.id.txtThreshold)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)

        val seek = findViewById<SeekBar>(R.id.seekThreshold)
        seek.progress = AppPrefs.minimumSpeed(this) - 5
        txtThreshold.text = "Continue above ${AppPrefs.minimumSpeed(this)} km/h"
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val speed = progress + 5
                txtThreshold.text = "Continue above $speed km/h"
                if (fromUser) AppPrefs.setMinimumSpeed(this@MainActivity, speed)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        btnStart.setOnClickListener { requestPermissionsAndStart() }
        btnStop.setOnClickListener {
            startService(Intent(this, RideService::class.java).setAction(RideService.ACTION_STOP))
            Toast.makeText(this, "Stopping ride monitoring", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnMaps).setOnClickListener {
            val launch = packageManager.getLaunchIntentForPackage("com.google.android.apps.maps")
            if (launch != null) startActivity(launch)
            else Toast.makeText(this, "Google Maps not found", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(updateUi)
        handler.post(updateUi)
    }

    override fun onPause() {
        handler.removeCallbacks(updateUi)
        super.onPause()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                info.resolveInfo.serviceInfo.packageName == packageName &&
                    info.resolveInfo.serviceInfo.name.endsWith(".MapsAccessibilityService")
            }
    }

    private fun requestPermissionsAndStart() {
        if (!isAccessibilityEnabled()) {
            AlertDialog.Builder(this)
                .setTitle("Enable Accessibility first")
                .setMessage("Enable Maps Auto-Continue in Android Accessibility settings, then return here to start your ride.")
                .setPositiveButton("Open settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            val permissions = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            requestPermissions(permissions.toTypedArray(), PERMISSION_REQUEST)
            return
        }
        startMonitoring()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                startMonitoring()
            } else {
                Toast.makeText(this, "Precise GPS permission is required", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startMonitoring() {
        try {
            startForegroundService(Intent(this, RideService::class.java).setAction(RideService.ACTION_START))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not start: ${e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshUi() {
        if (!::txtRide.isInitialized) return
        txtRide.text = if (RideState.riding) "Ride: Monitoring active" else "Ride: Not started"
        txtAccessibility.text = when {
            !isAccessibilityEnabled() -> "Accessibility: Disabled"
            RideState.accessibilityConnected -> "Accessibility: Connected to Google Maps"
            else -> "Accessibility: Enabled, connecting…"
        }
        val now = SystemClock.elapsedRealtime()
        val ageMs = now - RideState.lastFixElapsedMs
        txtSpeed.text = when {
            !RideState.riding -> "GPS: Idle"
            RideState.speedKmh == null || RideState.lastFixElapsedMs == 0L -> "GPS: Waiting for accurate GPS speed"
            ageMs > 5000L -> "GPS: Stale signal — no automatic taps"
            else -> "GPS: %.1f km/h (fix %.1f s ago)".format(
                RideState.speedKmh, ageMs.coerceAtLeast(0L) / 1000f
            )
        }
        txtLastAction.text = "Detector: ${RideState.lastAction}"
        btnStart.isEnabled = !RideState.riding
        btnStop.isEnabled = RideState.riding
    }
}
