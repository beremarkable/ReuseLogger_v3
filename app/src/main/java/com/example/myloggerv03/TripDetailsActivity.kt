package com.example.myloggerv03

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class TripDetailsActivity : AppCompatActivity() {

    private lateinit var tripName: TextView
    private lateinit var tripDate: TextView
    private lateinit var gpsData: TextView
    private lateinit var temperatureData: TextView
    private lateinit var shockData: TextView
    private lateinit var notes: TextView
    private lateinit var exportButton: Button
    private lateinit var btnViewMap: Button

    private lateinit var db: AppDatabase
    private lateinit var tripDao: TripDao
    private lateinit var trip: Trip
    private var tripId: Int = -1

    // ActivityResultLauncher for creating documents
    private lateinit var createFileLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trip_detail)

        // Initialize UI elements
        tripName = findViewById(R.id.trip_name)
        tripDate = findViewById(R.id.trip_date)
        gpsData = findViewById(R.id.gps_data)
        temperatureData = findViewById(R.id.temperature_data)
        shockData = findViewById(R.id.shock_data)
        notes = findViewById(R.id.notes)
        exportButton = findViewById(R.id.export_data_button)
        btnViewMap = findViewById(R.id.btn_view_map)

        // Initialize ActivityResultLauncher
        createFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.also { uri ->
                    writeTripDataToUri(uri)
                }
            }
        }

        // Initialize BottomNavigationView
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigationView.setOnItemSelectedListener { item ->
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

        // Get trip ID from intent
        tripId = intent.getIntExtra("TRIP_ID", -1)

        if (tripId != -1) {
            db = AppDatabase.getDatabase(applicationContext)
            tripDao = db.tripDao()

            // Fetch trip data on a background thread
            CoroutineScope(Dispatchers.IO).launch {
                trip = tripDao.getById(tripId) ?: return@launch
                val locationDao = db.locationDao()
                val locations = locationDao.getLocationsForTrip(tripId)

                val shockDao = db.shockEventDao()
                val shockEvents = shockDao.getShockEventsForTrip(tripId)

                val temperatureDao = db.temperatureDao()
                val temperatureReadings = temperatureDao.getTemperatureReadingsForTrip(tripId)

                withContext(Dispatchers.Main) {
                    // Set data to UI elements
                    tripName.text = trip.name
                    tripDate.text = trip.date ?: "Unknown Date"

                    // Display GPS data
                    displayGPSData(locations)

                    // Display temperature data
                    displayTemperatureData(temperatureReadings)

                    // Display shock data
                    displayShockData(shockEvents)

                    // Display notes
                    notes.text = if (!trip.notes.isNullOrEmpty()) trip.notes else "No Notes Available"
                }
            }
        } else {
            Toast.makeText(this, "Trip not found", Toast.LENGTH_SHORT).show()
            finish()
        }

        // Set up button listeners
        exportButton.setOnClickListener {
            exportTripData()
        }

        btnViewMap.setOnClickListener {
            val intent = Intent(this, MapActivity::class.java)
            intent.putExtra("TRIP_ID", tripId)
            startActivity(intent)
        }
    }

    private fun displayGPSData(locations: List<LocationEntity>) {
        val gpsDataText = locations.joinToString(separator = "\n") {
            "Lat: ${it.latitude}, Lon: ${it.longitude}, Time: ${
                SimpleDateFormat(
                    "HH:mm:ss",
                    Locale.getDefault()
                ).format(Date(it.timestamp))
            }"
        }
        gpsData.text = if (gpsDataText.isNotEmpty()) gpsDataText else "No GPS Data Available"
    }

    private fun displayTemperatureData(temperatureReadings: List<TemperatureEntity>) {
        val formattedTemperatureData =
            if (temperatureReadings.isNotEmpty()) {
                temperatureReadings.joinToString(separator = "\n") { reading ->
                    val formattedTime = SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss",
                        Locale.getDefault()
                    ).format(Date(reading.timestamp))
                    "${String.format("%.2f", reading.temperature)}°C at $formattedTime"
                }
            } else {
                "No Temperature Data Available"
            }
        temperatureData.text = formattedTemperatureData
    }

    private fun displayShockData(shockEvents: List<ShockEvent>) {
        val formattedShockData =
            if (shockEvents.isNotEmpty()) {
                shockEvents.joinToString(separator = "\n") { shockEvent ->
                    val formattedTime = SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss",
                        Locale.getDefault()
                    ).format(Date(shockEvent.timestamp))
                    "Magnitude: ${String.format("%.2f", shockEvent.magnitude)}N at $formattedTime"
                }
            } else {
                "No Shock Data Available"
            }
        shockData.text = formattedShockData
    }

    private fun exportTripData() {
        val uniqueFilename = generateUniqueFilename(tripId)
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, uniqueFilename)
        }
        createFileLauncher.launch(intent)
    }

    private fun generateUniqueFilename(tripId: Int): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "Trip_${tripId}_$timestamp.csv"
    }

    private fun writeTripDataToUri(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val writer = outputStream.bufferedWriter()

                    // Write Trip Name and Date
                    writer.write("Trip Name,${trip.name}\n")
                    writer.write("Date,${trip.date ?: "Unknown Date"}\n")
                    Log.d("TripDetailsActivity", "Trip Name: ${trip.name}, Date: ${trip.date}")

                    // Write GPS Data
                    writer.write("GPS Data\n")
                    val locationDao = db.locationDao()
                    val locations = locationDao.getLocationsForTrip(trip.id)
                    Log.d("TripDetailsActivity", "Number of locations fetched: ${locations.size}")
                    if (locations.isEmpty()) {
                        writer.write("No GPS Data Available\n")
                    } else {
                        locations.forEach { location ->
                            val formattedTime = SimpleDateFormat(
                                "yyyy-MM-dd HH:mm:ss",
                                Locale.getDefault()
                            ).format(Date(location.timestamp))
                            writer.write("${location.latitude},${location.longitude},$formattedTime\n")
                        }
                    }

                    // Write Temperature Data
                    writer.write("Temperature Data\n")
                    val temperatureDao = db.temperatureDao()
                    val temperatureReadings = temperatureDao.getTemperatureReadingsForTrip(trip.id)
                    if (temperatureReadings.isEmpty()) {
                        writer.write("No Temperature Data Available\n")
                    } else {
                        temperatureReadings.forEach { reading ->
                            val formattedTime = SimpleDateFormat(
                                "yyyy-MM-dd HH:mm:ss",
                                Locale.getDefault()
                            ).format(Date(reading.timestamp))
                            writer.write("${reading.temperature}°C,$formattedTime\n")
                        }
                    }

                    // Write Shock Data
                    writer.write("Shock Data\n")
                    val shockDao = db.shockEventDao()
                    val shockEvents = shockDao.getShockEventsForTrip(trip.id)
                    Log.d("TripDetailsActivity", "Number of shock events fetched: ${shockEvents.size}")
                    if (shockEvents.isEmpty()) {
                        writer.write("No Shock Data Available\n")
                    } else {
                        shockEvents.forEach { shockEvent ->
                            val formattedTime = SimpleDateFormat(
                                "yyyy-MM-dd HH:mm:ss",
                                Locale.getDefault()
                            ).format(Date(shockEvent.timestamp))
                            writer.write("${shockEvent.magnitude}N,$formattedTime\n")
                        }
                    }

                    // Write Notes
                    writer.write("Notes\n")
                    writer.write("${trip.notes ?: "No Notes"}\n")
                    Log.d("TripDetailsActivity", "Notes: ${trip.notes}")

                    writer.flush()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@TripDetailsActivity,
                            "Data exported successfully",
                            Toast.LENGTH_LONG
                        ).show()
                        Log.d("TripDetailsActivity", "CSV export completed successfully.")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@TripDetailsActivity,
                        "Error exporting data: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.e("TripDetailsActivity", "Error exporting data: ${e.message}")
                }
            }
        }
    }
}