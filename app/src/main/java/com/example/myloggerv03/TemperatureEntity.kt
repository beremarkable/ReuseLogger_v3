package com.example.myloggerv03

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "temperatureentity")
data class TemperatureEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tripId: Int,
    val temperature: Float,
    val timestamp: Long
)