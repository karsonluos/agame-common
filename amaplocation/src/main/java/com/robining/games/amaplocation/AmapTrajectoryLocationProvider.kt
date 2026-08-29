package com.robining.games.amaplocation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.robining.games.location.api.LastLocationStore
import com.robining.games.location.api.LocationTraceLog
import com.robining.games.location.api.TrajectoryLocationProvider
import com.robining.games.location.api.TrajectoryLocationSample

class AmapTrajectoryLocationProvider(context: Context) : TrajectoryLocationProvider {
    private val appContext = context.applicationContext
    private var client: AMapLocationClient? = null
    private var listener: TrajectoryLocationProvider.Listener? = null

    override val id: String = "amap"
    override val displayName: String = "高德定位"

    @SuppressLint("MissingPermission")
    override fun start(listener: TrajectoryLocationProvider.Listener): Boolean {
        if (client != null) return true
        val fine = appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            listener.onError("未授予定位权限")
            return false
        }
        this.listener = listener
        return runCatching {
            AMapLocationClient(appContext).also { locationClient ->
                locationClient.setLocationOption(
                    AMapLocationClientOption().apply {
                        locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                        interval = LOCATION_INTERVAL_MILLIS
                        isOnceLocation = false
                        isNeedAddress = false
                        isLocationCacheEnable = false
                    },
                )
                locationClient.setLocationListener(::onLocation)
                locationClient.startLocation()
                client = locationClient
            }
            LocationTraceLog.info(appContext, "amap START mode=high_accuracy interval=$LOCATION_INTERVAL_MILLIS")
            true
        }.getOrElse { error ->
            this.listener = null
            listener.onError("高德定位启动失败：${error.message}", error)
            false
        }
    }

    override fun stop() {
        client?.let { locationClient ->
            locationClient.stopLocation()
            locationClient.onDestroy()
        }
        client = null
        listener = null
        LocationTraceLog.info(appContext, "amap STOP")
    }

    private fun onLocation(location: AMapLocation?) {
        val value = location ?: return
        if (value.errorCode != AMapLocation.LOCATION_SUCCESS) {
            val message = "高德定位失败 ${value.errorCode}：${value.errorInfo}"
            LocationTraceLog.warn(appContext, message)
            listener?.onError(message)
            return
        }
        // AMap returns GCJ-02 in mainland China. Convert it so the neutral contract stays WGS-84.
        val coordinate = AmapLocationCoordinateTransform.gcj02ToWgs84(value.latitude, value.longitude)
        val sample = TrajectoryLocationSample(
            latitude = coordinate.first,
            longitude = coordinate.second,
            altitudeMeters = value.takeIf { it.hasAltitude() }?.altitude,
            accuracyMeters = value.takeIf { it.hasAccuracy() }?.accuracy,
            recordedAtMillis = value.time.takeIf { it > 0L } ?: System.currentTimeMillis(),
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
            speedMetersPerSecond = value.takeIf { it.hasSpeed() }?.speed,
            speedAccuracyMetersPerSecond = null,
        )
        LastLocationStore.save(appContext, sample.latitude, sample.longitude, sample.recordedAtMillis)
        LocationTraceLog.info(appContext, "amap SELECT type=${value.locationType} lat=${sample.latitude} lng=${sample.longitude} accuracy=${sample.accuracyMeters}")
        listener?.onLocation(sample)
        if (value.hasBearing()) listener?.onBearing(value.bearing.toDouble())
    }

    private companion object {
        const val LOCATION_INTERVAL_MILLIS = 1_000L
    }
}
