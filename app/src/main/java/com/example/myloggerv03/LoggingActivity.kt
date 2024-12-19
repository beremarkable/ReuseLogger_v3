package com.example.myloggerv03

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class LoggingActivity : AppCompatActivity() {

    private lateinit var txtLoggingStatus: TextView
    private lateinit var txtTemperatureData: TextView
    private lateinit var txtShockData: TextView
    private lateinit var btnEndTrip: Button
    private lateinit var btnAddNote: Button
    private lateinit var btnViewNotes: Button

    private var isLogging: Boolean = true
    private var tripId: Int = 0

    private var isShockEnabled: Boolean = false
    private var isTemperatureEnabled: Boolean = false
    private var temperatureSetting: String = "Medium"
    private var gpsInterval: Long = 10 * 60 * 1000L

    private val temperatureViewModel: TemperatureViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logging)

        txtLoggingStatus = findViewById(R.id.txt_logging_status)
        txtTemperatureData = findViewById(R.id.txt_temperature_data)
        txtShockData = findViewById(R.id.txt_shock_data)
        btnEndTrip = findViewById(R.id.btn_end_trip)
        btnAddNote = findViewById(R.id.btn_add_note)
        btnViewNotes = findViewById(R.id.btn_view_notes)

        txtLoggingStatus.text = "Trip Logging Active"

        tripId = SharedPrefHelper.getCurrentTripId(this)

        gpsInterval = intent.getLongExtra("GPS_INTERVAL", 10 * 60 * 1000L)
        isShockEnabled = intent.getBooleanExtra("isShockEnabled", false)
        isTemperatureEnabled = intent.getBooleanExtra("isTemperatureEnabled", false)
        temperatureSetting = intent.getStringExtra("temperatureSetting") ?: "Medium"

        if (tripId == 0) {
            Log.e("LoggingActivity", "No active trip found. Exiting.")
            Toast.makeText(this, "No active trip found.", Toast.LENGTH_SHORT).show()
            finish()
            return
        } else {
            checkLocationPermissions()
            if (isShockEnabled) {
                startShockDetectionService()
            }
            if (isTemperatureEnabled) {
                startTemperatureService()
            }

            temperatureViewModel.getLatestTemperature(tripId).observe(this, Observer { temperatureEntity ->
                if (temperatureEntity != null) {
                    txtTemperatureData.text = "Latest Temperature: ${String.format("%.2f", temperatureEntity.temperature)}°C"
                } else {
                    txtTemperatureData.text = "No temperature data available."
                }
            })
        }

        btnEndTrip.setOnClickListener {
            endTrip()
        }

        btnAddNote.setOnClickListener {
            addNote()
        }

        btnViewNotes.setOnClickListener {
            viewNotes()
        }
    }

    private fun endTrip() {
        isLogging = false
        txtLoggingStatus.text = "Logging Stopped"

        stopService(Intent(this, LocationService::class.java))
        if (isShockEnabled) {
            stopShockDetectionService()
        }
        if (isTemperatureEnabled) {
            stopTemperatureService()
        }

        // Retrieve and prepare trip data
        prepareTripDataForUpload()
    }

    private fun prepareTripDataForUpload() {
        if (tripId == 0) {
            Log.e("LoggingActivity", "Cannot prepare trip data without a valid tripId")
            return
        }

        val db = AppDatabase.getDatabase(applicationContext)
        val tripDao = db.tripDao()
        val temperatureDao = db.temperatureDao()
        val locationDao = db.locationDao()
        val shockEventDao = db.shockEventDao()

        CoroutineScope(Dispatchers.IO).launch {
            val trip = tripDao.getById(tripId)
            if (trip != null) {
                // Update notes
                trip.notes = tripNotes.joinToString("\n")

                // Retrieve temperature data
                val tempEntities = temperatureDao.getTemperatureReadingsForTrip(tripId)
                trip.temperatureData = tempEntities.map {
                    TemperatureData(it.temperature, it.timestamp)
                }

                // Retrieve GPS data
                val gpsEntities = locationDao.getLocationsForTrip(tripId)
                trip.gpsData = gpsEntities.map {
                    GpsData(it.latitude, it.longitude, it.timestamp)
                }

                // Retrieve shock data
                val shockEntities = shockEventDao.getShockEventsForTrip(tripId)
                trip.shockData = shockEntities.map {
                    ShockData(it.magnitude, it.timestamp)
                }

                // Update trip in local database
                tripDao.update(trip)

                // Upload to Firebase
                withContext(Dispatchers.Main) {
                    checkWiFiAndUpload(trip)
                }
            } else {
                Log.e("LoggingActivity", "Trip not found with ID: $tripId")
            }

            // Clear current trip ID and finish activity
            withContext(Dispatchers.Main) {
                SharedPrefHelper.clearCurrentTripId(this@LoggingActivity)
                tripId = 0
                Toast.makeText(this@LoggingActivity, "Trip ended and data saved.", Toast.LENGTH_SHORT).show()
                val intent = Intent(this@LoggingActivity, SavedTripsActivity::class.java)
                startActivity(intent)
                finish()
            }
        }
    }

    private fun addNote() {
        val note = "Note ${tripNotes.size + 1} at ${
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        }"
        tripNotes.add(note)
        Toast.makeText(this, "Note added", Toast.LENGTH_SHORT).show()
    }

    private fun viewNotes() {
        val notes = tripNotes.joinToString("\n")
        txtShockData.text = notes
    }

    private fun checkLocationPermissions() {
        if (PermissionsHelper.hasLocationPermissions(this)) {
            startLocationService()
        } else {
            PermissionsHelper.requestLocationPermissions(this)
        }
    }

    private fun startLocationService() {
        if (tripId == 0) {
            Log.e("LoggingActivity", "Cannot start LocationService without a valid tripId")
            return
        }

        val serviceIntent = Intent(this, LocationService::class.java).apply {
            putExtra("GPS_INTERVAL", gpsInterval)
            putExtra("TRIP_ID", tripId)
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        Log.d("LoggingActivity", "LocationService started with tripId: $tripId and gpsInterval: $gpsInterval")
    }

    private fun startShockDetectionService() {
        if (tripId == 0) {
            Log.e("LoggingActivity", "Cannot start ShockDetectionService without a valid tripId")
            return
        }

        val serviceIntent = Intent(this, ShockDetectionService::class.java).apply {
            putExtra("TRIP_ID", tripId)
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        Log.d("LoggingActivity", "ShockDetectionService started for tripId: $tripId")
    }

    private fun stopShockDetectionService() {
        val serviceIntent = Intent(this, ShockDetectionService::class.java)
        stopService(serviceIntent)
        Log.d("LoggingActivity", "ShockDetectionService stopped for tripId: $tripId")
    }

    private fun startTemperatureService() {
        if (tripId == 0) {
            Log.e("LoggingActivity", "Cannot start TemperatureService without a valid tripId")
            return
        }

        val serviceIntent = Intent(this, TemperatureService::class.java).apply {
            putExtra("TRIP_ID", tripId)
            putExtra("temperatureSetting", temperatureSetting)
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        Log.d("LoggingActivity", "TemperatureService started for tripId: $tripId with setting: $temperatureSetting")
    }

    private fun stopTemperatureService() {
        val serviceIntent = Intent(this, TemperatureService::class.java)
        stopService(serviceIntent)
        Log.d("LoggingActivity", "TemperatureService stopped for tripId: $tripId")
    }

    private fun checkWiFiAndUpload(trip: Trip) {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        val isWiFi = networkInfo != null && networkInfo.type == ConnectivityManager.TYPE_WIFI

        if (isWiFi) {
            uploadTripData(trip)
        } else {
            Toast.makeText(this, "No Wi-Fi connection. Trip data will not be uploaded.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadTripData(trip: Trip) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            val databaseRef = FirebaseDatabase.getInstance().getReference("users").child(userId).child("trips")
            val tripKey = databaseRef.push().key

            if (tripKey != null) {
                databaseRef.child(tripKey).setValue(trip)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Trip data uploaded successfully.", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to upload trip data: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        } else {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isLogging = false
        Log.d("LoggingActivity", "LoggingActivity destroyed and data collection stopped.")
    }

    companion object {
        private val tripNotes = mutableListOf<String>()
    }
}