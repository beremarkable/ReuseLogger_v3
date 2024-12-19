package com.example.myloggerv03

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LocationDao {
    @Insert
    suspend fun insert(location: LocationEntity): Long

    @Query("SELECT * FROM locations WHERE tripId = :tripId")
    suspend fun getLocationsForTrip(tripId: Int): List<LocationEntity>

    @Query("DELETE FROM locations WHERE tripId = :tripId")
    suspend fun deleteLocationsForTrip(tripId: Int)
}