package com.example.myloggerv03

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface TemperatureDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(temperature: TemperatureEntity): Long

    @Query("SELECT * FROM temperatureentity WHERE tripId = :tripId ORDER BY timestamp DESC LIMIT 1")
    fun getLatestTemperature(tripId: Int): LiveData<TemperatureEntity?>

    @Query("SELECT * FROM temperatureentity WHERE tripId = :tripId ORDER BY timestamp ASC")
    suspend fun getTemperatureReadingsForTrip(tripId: Int): List<TemperatureEntity>
}