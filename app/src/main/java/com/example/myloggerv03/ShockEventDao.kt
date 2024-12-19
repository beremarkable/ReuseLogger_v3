package com.example.myloggerv03

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ShockEventDao {
    @Insert
    suspend fun insert(shockEvent: ShockEvent): Long

    @Query("SELECT * FROM shock_events WHERE tripId = :tripId")
    suspend fun getShockEventsForTrip(tripId: Int): List<ShockEvent>

    @Query("DELETE FROM shock_events WHERE tripId = :tripId")
    suspend fun deleteShockEventsForTrip(tripId: Int)
}