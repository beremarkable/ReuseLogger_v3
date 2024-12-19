package com.example.myloggerv03

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var btnNewTrip: Button
    private lateinit var btnSavedTrips: Button
    private lateinit var btnSettings: ImageButton
    private lateinit var btnLogout: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()

        // Check if the user is logged in, if not redirect to LoginActivity
        if (auth.currentUser == null) {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish() // Close MainActivity
        }

        btnNewTrip = findViewById(R.id.btn_new_trip)
        btnSavedTrips = findViewById(R.id.btn_saved_trips)
        btnSettings = findViewById(R.id.btn_settings)
        btnLogout = findViewById(R.id.btn_logout)

        // Check and request permissions
        if (!PermissionsHelper.hasLocationPermissions(this)) {
            PermissionsHelper.requestLocationPermissions(this)
        }

        btnNewTrip.setOnClickListener {
            // Navigate to New Trip Parameters Page
            val intent = Intent(this, NewTripParametersActivity::class.java)
            startActivity(intent)
        }

        btnSavedTrips.setOnClickListener {
            // Navigate to Saved Trips List
            val intent = Intent(this, SavedTripsActivity::class.java)
            startActivity(intent)
        }

        btnSettings.setOnClickListener {
            // Navigate to Settings/Menu
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        // Logout button functionality
        btnLogout.setOnClickListener {
            auth.signOut() // Log out the user
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish() // Close MainActivity
        }
    }

    // Handle permission result
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionsHelper.REQUEST_LOCATION_PERMISSIONS) {
            if (PermissionsHelper.hasLocationPermissions(this)) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}