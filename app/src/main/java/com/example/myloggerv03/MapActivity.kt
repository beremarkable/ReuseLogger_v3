package com.example.myloggerv03

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.*

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private var tripId: Int = -1  // Trip ID passed from intent
    private lateinit var locationDao: LocationDao

    // Coroutine scope for this activity
    private val mapActivityScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        // Get trip ID from intent
        tripId = intent.getIntExtra("TRIP_ID", -1)

        // Obtain the SupportMapFragment and get notified when the map is ready.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map_fragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Initialize database and DAO
        val db = AppDatabase.getDatabase(applicationContext)
        locationDao = db.locationDao()

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
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Load and display GPS data for the trip
        loadAndDisplayTripLocations()
    }

    private fun loadAndDisplayTripLocations() {
        if (tripId == -1) {
            // Handle invalid tripId error here, if necessary
            return
        }

        // Use a coroutine to fetch locations in the background and update UI on the main thread
        mapActivityScope.launch {
            val locations = withContext(Dispatchers.IO) {
                locationDao.getLocationsForTrip(tripId)
            }

            if (locations.isNotEmpty()) {
                val polylineOptions = PolylineOptions()

                // Add each location to the polyline and place a marker on the map
                for (location in locations) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    polylineOptions.add(latLng)
                    // Add a marker for each location
                    mMap.addMarker(MarkerOptions().position(latLng).title("Logged Location"))
                }

                // Add the polyline to the map
                mMap.addPolyline(polylineOptions)

                // Move the camera to the first location
                mMap.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(locations[0].latitude, locations[0].longitude),
                        15f
                    )
                )
            } else {
                // Optionally, show a message if there are no locations to display
                Toast.makeText(this@MapActivity, "No logged locations to display", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel the coroutine scope to avoid memory leaks
        mapActivityScope.cancel()
    }
}