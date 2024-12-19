package com.example.myloggerv03

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Ignore

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true) var id: Int = 0,
    var notes: String? = "",
    var name: String,
    var date: String
) {
    @Ignore
    var temperatureData: List<TemperatureData> = emptyList()

    @Ignore
    var gpsData: List<GpsData> = emptyList()

    @Ignore
    var shockData: List<ShockData> = emptyList()
}