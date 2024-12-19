package com.example.myloggerv03

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SavedTripsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TripAdapter
    private var tripList: MutableList<Trip> = mutableListOf()
    private lateinit var db: AppDatabase
    private lateinit var tripDao: TripDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_trips)

        db = AppDatabase.getDatabase(applicationContext)
        tripDao = db.tripDao()

        recyclerView = findViewById(R.id.recycler_view_saved_trips)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = TripAdapter(tripList,
            onItemClicked = { trip ->
                val intent = Intent(this, TripDetailsActivity::class.java)
                intent.putExtra("TRIP_ID", trip.id)
                startActivity(intent)
            },
            onDeleteClicked = { trip ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        tripDao.delete(trip)
                    }
                    tripList.remove(trip)
                    adapter.notifyDataSetChanged()
                    Toast.makeText(this@SavedTripsActivity, "Trip deleted", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        )

        recyclerView.adapter = adapter

        // Fetch trips from the database using coroutines
        lifecycleScope.launch {
            tripDao.getAll().collect { trips ->
                tripList.clear()
                tripList.addAll(trips)
                adapter.notifyDataSetChanged()
            }
        }
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
                    // Stay on this screen (SavedTripsActivity)
                    true
                }

                else -> false
            }
        }
    }
}


