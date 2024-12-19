package com.example.myloggerv03

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips")
    fun getAll(): Flow<List<Trip>>

    @Insert
    suspend fun insert(trip: Trip): Long

    @Update
    suspend fun update(trip: Trip): Int

    @Delete
    suspend fun delete(trip: Trip): Int

    @Query("SELECT * FROM trips WHERE id = :tripId")
    suspend fun getById(tripId: Int): Trip?
}