package com.example.myloggerv03

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TemperatureViewModel(application: Application) : AndroidViewModel(application) {

    private val temperatureDao = AppDatabase.getDatabase(application).temperatureDao()

    // Retrieve the latest temperature reading for the given tripId
    fun getLatestTemperature(tripId: Int): LiveData<TemperatureEntity?> {
        return temperatureDao.getLatestTemperature(tripId)
    }

    // Optional: Retrieve all temperature readings for the given tripId
    suspend fun getAllTemperatureReadings(tripId: Int): List<TemperatureEntity> {
        return withContext(Dispatchers.IO) {
            temperatureDao.getTemperatureReadingsForTrip(tripId)
        }
    }
}