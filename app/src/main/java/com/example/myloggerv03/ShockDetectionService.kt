package com.example.myloggerv03

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlin.math.sqrt

class ShockDetectionService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private var tripId: Int = 0
    private var isShocking = false
    private val SHOCK_THRESHOLD = 15.0f // Adjust based on testing
    private val COOLDOWN_PERIOD = 1000L // 1 second cooldown to prevent multiple detections

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Shock Detection Active")
            .setContentText("Monitoring for shock events during your trip.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(NOTIFICATION_ID, notification)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        tripId = intent?.getIntExtra("TRIP_ID", 0) ?: 0
        if (tripId != 0) {
            accelerometer?.also { accel ->
                sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    // SensorEventListener Methods
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val x = it.values[0]
            val y = it.values[1]
            val z = it.values[2]

            val accelerationMagnitude = sqrt(x * x + y * y + z * z)

            if (accelerationMagnitude > SHOCK_THRESHOLD && !isShocking) {
                isShocking = true
                logShockEvent(accelerationMagnitude, System.currentTimeMillis())

                // Start cooldown
                CoroutineScope(Dispatchers.IO).launch {
                    delay(COOLDOWN_PERIOD)
                    isShocking = false
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }

    private fun logShockEvent(magnitude: Float, timestamp: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val shockDao = db.shockEventDao()
            val shockEvent = ShockEvent(
                tripId = tripId,
                magnitude = magnitude,
                timestamp = timestamp
            )
            shockDao.insert(shockEvent)
            // Optional: Notify the user of a detected shock
            // sendShockNotification(shockEvent)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Shock Detection Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    companion object {
        const val CHANNEL_ID = "ShockDetectionServiceChannel"
        const val NOTIFICATION_ID = 1
    }
}