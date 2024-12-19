package com.example.myloggerv03

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tripId: Int,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)