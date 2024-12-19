package com.example.myloggerv03

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Trip::class, LocationEntity::class, ShockEvent::class, TemperatureEntity::class],
    version = 2, // Increment if you've added or modified entities
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao
    abstract fun locationDao(): LocationDao
    abstract fun shockEventDao(): ShockEventDao
    abstract fun temperatureDao(): TemperatureDao // Add this line

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "my_logger_database"
                )
                    .fallbackToDestructiveMigration() // Handle migrations appropriately
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}