package com.example.gpsmock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MockLocationService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START_MOCK"
        const val ACTION_STOP = "ACTION_STOP_MOCK"
        const val ACTION_UPDATE = "ACTION_UPDATE_LOCATION"
        const val EXTRA_LATITUDE = "EXTRA_LATITUDE"
        const val EXTRA_LONGITUDE = "EXTRA_LONGITUDE"
        const val EXTRA_ALTITUDE = "EXTRA_ALTITUDE"
        const val EXTRA_ACCURACY = "EXTRA_ACCURACY"
        const val EXTRA_SPEED = "EXTRA_SPEED"
        const val CHANNEL_ID = "MockLocationChannel"
        const val NOTIFICATION_ID = 1001
        const val UPDATE_INTERVAL_MS = 1000L

        var isRunning = false
        var currentLat = 0.0
        var currentLng = 0.0
    }

    private lateinit var locationManager: LocationManager
    private var mockJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private var latitude = 25.0330  // 預設：台北 101
    private var longitude = 121.5654
    private var altitude = 10.0
    private var accuracy = 3.0f
    private var speed = 0.0f

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                intent.extras?.let { extras ->
                    latitude = extras.getDouble(EXTRA_LATITUDE, latitude)
                    longitude = extras.getDouble(EXTRA_LONGITUDE, longitude)
                    altitude = extras.getDouble(EXTRA_ALTITUDE, altitude)
                    accuracy = extras.getFloat(EXTRA_ACCURACY, accuracy)
                    speed = extras.getFloat(EXTRA_SPEED, speed)
                }
                startMocking()
            }
            ACTION_STOP -> stopMocking()
            ACTION_UPDATE -> {
                intent.extras?.let { extras ->
                    latitude = extras.getDouble(EXTRA_LATITUDE, latitude)
                    longitude = extras.getDouble(EXTRA_LONGITUDE, longitude)
                    altitude = extras.getDouble(EXTRA_ALTITUDE, altitude)
                    accuracy = extras.getFloat(EXTRA_ACCURACY, accuracy)
                    speed = extras.getFloat(EXTRA_SPEED, speed)
                }
                currentLat = latitude
                currentLng = longitude
            }
        }
        return START_STICKY
    }

    private fun startMocking() {
        startForeground(NOTIFICATION_ID, buildNotification())
        isRunning = true
        currentLat = latitude
        currentLng = longitude

        addMockProvider(LocationManager.GPS_PROVIDER)
        addMockProvider(LocationManager.NETWORK_PROVIDER)

        mockJob = scope.launch {
            while (isRunning) {
                pushMockLocation(LocationManager.GPS_PROVIDER)
                pushMockLocation(LocationManager.NETWORK_PROVIDER)
                delay(UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun stopMocking() {
        isRunning = false
        mockJob?.cancel()

        removeMockProvider(LocationManager.GPS_PROVIDER)
        removeMockProvider(LocationManager.NETWORK_PROVIDER)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun addMockProvider(provider: String) {
        try {
            locationManager.removeTestProvider(provider)
        } catch (_: Exception) {}

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                locationManager.addTestProvider(
                    provider,
                    false, false, false, false, true, true, true,
                    ProviderProperties.POWER_USAGE_LOW,
                    ProviderProperties.ACCURACY_FINE
                )
            } else {
                @Suppress("DEPRECATION")
                locationManager.addTestProvider(
                    provider,
                    false, false, false, false, true, true, true,
                    android.location.Criteria.POWER_LOW,
                    android.location.Criteria.ACCURACY_FINE
                )
            }
            locationManager.setTestProviderEnabled(provider, true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeMockProvider(provider: String) {
        try {
            locationManager.setTestProviderEnabled(provider, false)
            locationManager.removeTestProvider(provider)
        } catch (_: Exception) {}
    }

    private fun pushMockLocation(provider: String) {
        try {
            val loc = Location(provider).apply {
                this.latitude = currentLat
                this.longitude = currentLng
                this.altitude = altitude
                this.accuracy = accuracy
                this.speed = speed
                this.bearing = 0f
                this.time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    verticalAccuracyMeters = 1f
                    bearingAccuracyDegrees = 0.1f
                    speedAccuracyMetersPerSecond = 0.01f
                }
            }
            locationManager.setTestProviderLocation(provider, loc)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, MockLocationService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GPS 座標修改中")
            .setContentText("緯度: $latitude  經度: $longitude")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_delete, "停止", stopPending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "GPS Mock 服務",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "持續提供模擬 GPS 座標"
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        mockJob?.cancel()
        removeMockProvider(LocationManager.GPS_PROVIDER)
        removeMockProvider(LocationManager.NETWORK_PROVIDER)
    }
}
