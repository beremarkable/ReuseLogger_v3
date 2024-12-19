package com.example.myloggerv03

import android.content.Context
import android.content.SharedPreferences

object SharedPrefHelper {

    private const val PREFS_NAME = "myloggerv03_prefs"
    private const val KEY_CURRENT_TRIP_ID = "current_trip_id"

    fun setCurrentTripId(context: Context, tripId: Int) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_CURRENT_TRIP_ID, tripId).apply()
    }

    fun getCurrentTripId(context: Context): Int {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_CURRENT_TRIP_ID, 0)
    }

    fun clearCurrentTripId(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_CURRENT_TRIP_ID).apply()
    }
}