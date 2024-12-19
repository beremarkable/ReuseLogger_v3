package com.example.myloggerv03

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shock_events")
data class ShockEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tripId: Int,
    val magnitude: Float,
    val timestamp: Long
)