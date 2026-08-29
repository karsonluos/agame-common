package com.robining.games.location.api

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Vendor-neutral reverse-geocoding boundary. */
object LocationReverseGeocoder {
    suspend fun reverse(context: Context, latitude: Double, longitude: Double): String? {
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        return withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext null
            val locale = context.resources.configuration.locales[0]
            @Suppress("DEPRECATION")
            runCatching {
                val address = Geocoder(context, locale).getFromLocation(latitude, longitude, 1)
                    ?.firstOrNull() ?: return@runCatching null
                address.getAddressLine(0)?.takeIf(String::isNotBlank) ?: listOfNotNull(
                    address.thoroughfare,
                    address.subLocality,
                    address.locality,
                    address.adminArea,
                    address.countryName,
                ).filter(String::isNotBlank).distinct().joinToString(", ").takeIf(String::isNotBlank)
            }.getOrNull()
        }
    }
}

