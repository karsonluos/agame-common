package com.robining.games.mapboxmap

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.os.Looper
import android.util.Log
import com.mapbox.common.Cancelable
import com.mapbox.common.TelemetryLocationProvider
import com.mapbox.common.TelemetryUtils
import com.mapbox.common.location.DeviceLocationProvider
import com.mapbox.common.location.GetLocationCallback
import com.mapbox.common.location.LocationObserver

/** Map provider bootstrap kept out of the application module. */
internal object MapboxRuntime {
    @SuppressLint("RestrictedApi")
    fun initialize(context: Context) {
        runCatching {
            TelemetryLocationProvider.setDeviceLocationProvider(NoOpTelemetryLocationProvider)
            TelemetryUtils.setEventsCollectionState(false, null)
        }.onSuccess {
            Log.i(TAG, "Map telemetry collection disabled")
        }.onFailure {
            Log.e(TAG, "Unable to disable map telemetry collection", it)
        }
    }

    private const val TAG = "MapModule"

    private object NoOpTelemetryLocationProvider : DeviceLocationProvider {
        override fun addLocationObserver(observer: LocationObserver) = Unit
        override fun addLocationObserver(observer: LocationObserver, looper: Looper) = Unit
        override fun removeLocationObserver(observer: LocationObserver) = Unit
        override fun getLastLocation(callback: GetLocationCallback): Cancelable = Cancelable { }
        override fun requestLocationUpdates(pendingIntent: PendingIntent) = Unit
        override fun removeLocationUpdates(pendingIntent: PendingIntent) = Unit
        override fun getName(): String = "disabled-map-telemetry-location"
    }
}

