package com.robining.games.location.api

import android.content.Context

data class CachedLocation(
    val latitude: Double,
    val longitude: Double,
    val recordedAtMillis: Long,
)

object LastLocationStore {
    private const val PREFERENCES = "last_location_cache"
    private const val KEY_LATITUDE = "latitude"
    private const val KEY_LONGITUDE = "longitude"
    private const val KEY_RECORDED_AT = "recorded_at"

    fun save(context: Context, latitude: Double, longitude: Double, recordedAtMillis: Long) {
        if (!latitude.isFinite() || !longitude.isFinite()) return
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LATITUDE, latitude.toBits())
            .putLong(KEY_LONGITUDE, longitude.toBits())
            .putLong(KEY_RECORDED_AT, recordedAtMillis)
            .apply()
    }

    fun latest(context: Context, maxAgeMillis: Long, nowMillis: Long = System.currentTimeMillis()): CachedLocation? {
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val recordedAt = preferences.getLong(KEY_RECORDED_AT, 0L)
        if (recordedAt <= 0L || nowMillis - recordedAt !in 0..maxAgeMillis) return null
        return CachedLocation(
            latitude = Double.fromBits(preferences.getLong(KEY_LATITUDE, 0L)),
            longitude = Double.fromBits(preferences.getLong(KEY_LONGITUDE, 0L)),
            recordedAtMillis = recordedAt,
        )
    }
}

