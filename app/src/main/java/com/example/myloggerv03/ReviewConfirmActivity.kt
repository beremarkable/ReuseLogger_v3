package com.example.myloggerv03

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReviewConfirmActivity : AppCompatActivity() {

    private lateinit var txtGPSStatus: TextView
    private lateinit var txtGPSInterval: TextView
    private lateinit var txtTemperatureStatus: TextView
    private lateinit var txtTemperatureSetting: TextView
    private lateinit var txtShockStatus: TextView
    private lateinit var btnStartLogging: Button
    private lateinit var btnBack: Button

    private var isGPSEnabled: Boolean = true
    private var gpsInterval: String = "Every 10 minutes"
    private var isTemperatureEnabled: Boolean = true
    private var temperatureSetting: String = "Medium"
    private var isShockEnabled: Boolean = true

    private var gpsIntervalMinutes: Long = 10L // Default to 10 minutes
    private var tripId: Int = 0 // Will hold the new trip ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_review_confirm)

        // Initialize UI elements
        txtGPSStatus = findViewById(R.id.txt_gps_status)
        txtGPSInterval = findViewById(R.id.txt_gps_interval)
        txtTemperatureStatus = findViewById(R.id.txt_temperature_status)
        txtTemperatureSetting = findViewById(R.id.txt_temperature_setting)
        txtShockStatus = findViewById(R.id.txt_shock_status)
        btnStartLogging = findViewById(R.id.btn_start_logging)
        btnBack = findViewById(R.id.btn_back)

        // Initialize Bottom Navigation View
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigationView.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    // Navigate to Home (MainActivity)
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.navigation_new_trip -> {
                    // Navigate to NewTripParametersActivity
                    val intent = Intent(this, NewTripParametersActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.navigation_saved_trips -> {
                    // Navigate to SavedTripsActivity
                    val intent = Intent(this, SavedTripsActivity::class.java)
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }

        // Get parameters from intent
        isGPSEnabled = intent.getBooleanExtra("isGPSEnabled", true)
        gpsInterval = intent.getStringExtra("gpsInterval") ?: "Every 10 minutes"
        isTemperatureEnabled = intent.getBooleanExtra("isTemperatureEnabled", true)
        temperatureSetting = intent.getStringExtra("temperatureSetting") ?: "Medium"
        isShockEnabled = intent.getBooleanExtra("isShockEnabled", true)

        // Parse GPS interval
        gpsIntervalMinutes = when (gpsInterval) {
            "Every 5 minutes" -> 5L
            "Every 10 minutes" -> 10L
            "Every 15 minutes" -> 15L
            else -> 10L
        }

        // Display parameters
        txtGPSStatus.text = if (isGPSEnabled) "Enabled" else "Disabled"
        txtGPSInterval.text = gpsInterval
        txtTemperatureStatus.text = if (isTemperatureEnabled) "Enabled" else "Disabled"
        txtTemperatureSetting.text = temperatureSetting
        txtShockStatus.text = if (isShockEnabled) "Enabled" else "Disabled"

        // Handle button clicks
        btnStartLogging.setOnClickListener {
            // Generate or retrieve a trip ID
            createNewTrip()
        }

        btnBack.setOnClickListener {
            finish()
        }
    }

    private fun createNewTrip() {
        // Insert a new trip into the database and get the trip ID
        val db = AppDatabase.getDatabase(applicationContext)
        val tripDao = db.tripDao()
        val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val newTrip = Trip(
            name = "Trip $currentDate",
            date = currentDate,
            notes = ""
        )

        CoroutineScope(Dispatchers.IO).launch {
            tripId = tripDao.insert(newTrip).toInt()
            // Set the tripId in SharedPrefHelper to indicate an active trip
            SharedPrefHelper.setCurrentTripId(this@ReviewConfirmActivity, tripId)
            Log.d("ReviewConfirmActivity", "Trip created with ID: $tripId")

            withContext(Dispatchers.Main) {
                startLogging()
            }
        }
    }

    private fun startLogging() {
        // Navigate to LoggingActivity
        val intent = Intent(this, LoggingActivity::class.java).apply {
            putExtra("TRIP_ID", tripId)
            putExtra("GPS_INTERVAL", gpsIntervalMinutes * 60 * 1000L) // Convert minutes to milliseconds
            putExtra("isShockEnabled", isShockEnabled)
            putExtra("isTemperatureEnabled", isTemperatureEnabled)
            putExtra("temperatureSetting", temperatureSetting)
        }
        startActivity(intent)
        finish()
    }
}