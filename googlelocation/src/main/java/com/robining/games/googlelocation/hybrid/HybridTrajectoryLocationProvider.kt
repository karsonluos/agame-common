package com.robining.games.googlelocation.hybrid

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import android.view.WindowManager
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.robining.games.googlelocation.R
import com.robining.games.location.api.LastLocationStore
import com.robining.games.location.api.LocationTraceLog
import com.robining.games.location.api.TrajectoryLocationProvider
import com.robining.games.location.api.TrajectoryLocationSample

/** Current adapter: Google Fused with Android GPS/network fallback. */
class HybridTrajectoryLocationProvider(context: Context) :
    TrajectoryLocationProvider,
    LocationListener,
    SensorEventListener {

    private val appContext = context.applicationContext
    private val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)
    private val locationManager = appContext.getSystemService(LocationManager::class.java)
    private val sensorManager = appContext.getSystemService(SensorManager::class.java)
    private val windowManager = appContext.getSystemService(WindowManager::class.java)
    private val registeredNativeProviders = linkedSetOf<String>()
    private var listener: TrajectoryLocationProvider.Listener? = null
    private var listening = false
    private var fusedRequested = false
    private var lastFusedAtElapsedMillis = 0L
    private var lastGpsAtElapsedMillis = 0L
    private var lastSuppressedLogAtElapsedMillis = 0L

    override val id: String = "google-android-hybrid"
    override val displayName: String = appContext.getString(R.string.location_provider_name)

    private val fusedCallback = object : LocationCallback() {
        override fun onLocationAvailability(availability: LocationAvailability) {
            LocationTraceLog.info(appContext, "hybrid Fused availability=${availability.isLocationAvailable}")
        }

        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            if (location.ageMillis() > MAX_ACCEPTED_LOCATION_AGE_MILLIS) return
            lastFusedAtElapsedMillis = SystemClock.elapsedRealtime()
            publish(location, "fused")
        }
    }

    @SuppressLint("MissingPermission")
    override fun start(listener: TrajectoryLocationProvider.Listener): Boolean {
        if (listening) return true
        this.listener = listener
        val fine = appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            listener.onError(appContext.getString(R.string.location_permission_missing))
            this.listener = null
            return false
        }
        val locationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) ||
                runCatching { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)
        }
        if (!locationEnabled) {
            listener.onError(appContext.getString(R.string.location_provider_unavailable))
            this.listener = null
            return false
        }

        resetSession()
        locationManager.getProviders(true).toSet().let { enabled ->
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .filter(enabled::contains)
                .forEach(::requestNativeProvider)
        }
        requestFusedUpdates()
        listening = fusedRequested || registeredNativeProviders.isNotEmpty()
        if (!listening) {
            listener.onError(appContext.getString(R.string.location_provider_unavailable))
            this.listener = null
            return false
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        LocationTraceLog.info(appContext, "hybrid START fused=$fusedRequested native=$registeredNativeProviders")
        return true
    }

    override fun stop() {
        if (fusedRequested) fusedClient.removeLocationUpdates(fusedCallback)
        if (listening || registeredNativeProviders.isNotEmpty()) locationManager.removeUpdates(this)
        sensorManager.unregisterListener(this)
        listening = false
        fusedRequested = false
        registeredNativeProviders.clear()
        listener = null
        lastFusedAtElapsedMillis = 0L
        lastGpsAtElapsedMillis = 0L
        LocationTraceLog.info(appContext, "hybrid STOP")
    }

    private fun resetSession() {
        registeredNativeProviders.clear()
        lastFusedAtElapsedMillis = 0L
        lastGpsAtElapsedMillis = 0L
        lastSuppressedLogAtElapsedMillis = 0L
    }

    @SuppressLint("MissingPermission")
    private fun requestFusedUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MILLIS)
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MILLIS)
            .setWaitForAccurateLocation(false)
            .build()
        fusedRequested = runCatching {
            fusedClient.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper())
                .addOnFailureListener { error ->
                    LocationTraceLog.warn(appContext, "hybrid Fused registration failed: ${error.message}")
                }
        }.isSuccess
    }

    @SuppressLint("MissingPermission")
    private fun requestNativeProvider(provider: String) {
        if (provider in registeredNativeProviders) return
        runCatching {
            locationManager.requestLocationUpdates(provider, LOCATION_INTERVAL_MILLIS, 0f, this, Looper.getMainLooper())
        }.onSuccess {
            registeredNativeProviders += provider
        }.onFailure {
            LocationTraceLog.warn(appContext, "hybrid native registration failed provider=$provider: ${it.message}")
        }
    }

    override fun onLocationChanged(location: Location) {
        val now = SystemClock.elapsedRealtime()
        if (location.ageMillis() > MAX_ACCEPTED_LOCATION_AGE_MILLIS) return
        if (location.provider == LocationManager.GPS_PROVIDER) lastGpsAtElapsedMillis = now
        val fusedFresh = lastFusedAtElapsedMillis != 0L && now - lastFusedAtElapsedMillis <= FUSED_STALE_AFTER_MILLIS
        val gpsFresh = lastGpsAtElapsedMillis != 0L && now - lastGpsAtElapsedMillis <= GPS_STALE_AFTER_MILLIS
        val selected = !fusedFresh && (
            location.provider == LocationManager.GPS_PROVIDER ||
                location.provider == LocationManager.NETWORK_PROVIDER && !gpsFresh
            )
        if (selected) publish(location, "native:${location.provider}")
        else if (now - lastSuppressedLogAtElapsedMillis >= SUPPRESSED_LOG_INTERVAL_MILLIS) {
            lastSuppressedLogAtElapsedMillis = now
            LocationTraceLog.info(appContext, "hybrid native standby provider=${location.provider}")
        }
    }

    private fun publish(location: Location, source: String) {
        val sample = TrajectoryLocationSample(
            latitude = location.latitude,
            longitude = location.longitude,
            altitudeMeters = location.takeIf(Location::hasAltitude)?.altitude,
            accuracyMeters = location.takeIf(Location::hasAccuracy)?.accuracy,
            recordedAtMillis = location.time.takeIf { it > 0L } ?: System.currentTimeMillis(),
            elapsedRealtimeNanos = location.elapsedRealtimeNanos.takeIf { it > 0L } ?: SystemClock.elapsedRealtimeNanos(),
            speedMetersPerSecond = location.takeIf(Location::hasSpeed)?.speed,
            speedAccuracyMetersPerSecond = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasSpeedAccuracy()) {
                location.speedAccuracyMetersPerSecond
            } else null,
        )
        LastLocationStore.save(appContext, sample.latitude, sample.longitude, sample.recordedAtMillis)
        LocationTraceLog.info(
            appContext,
            "hybrid SELECT source=$source lat=${sample.latitude} lng=${sample.longitude} accuracy=${sample.accuracyMeters}",
        )
        listener?.onLocation(sample)
    }

    override fun onProviderEnabled(provider: String) {
        if (listening && provider in setOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            requestNativeProvider(provider)
        }
    }

    override fun onProviderDisabled(provider: String) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        if (!listening || event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        val rotation = FloatArray(9)
        val adjusted = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        @Suppress("DEPRECATION")
        val displayRotation = windowManager.defaultDisplay.rotation
        val (axisX, axisY) = when (displayRotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(rotation, axisX, axisY, adjusted)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(adjusted, orientation)
        listener?.onBearing((Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun Location.ageMillis(): Long =
        (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos).coerceAtLeast(0L) / 1_000_000L

    private companion object {
        const val LOCATION_INTERVAL_MILLIS = 1_000L
        const val MIN_UPDATE_INTERVAL_MILLIS = 500L
        const val FUSED_STALE_AFTER_MILLIS = 3_500L
        const val GPS_STALE_AFTER_MILLIS = 5_000L
        const val MAX_ACCEPTED_LOCATION_AGE_MILLIS = 15_000L
        const val SUPPRESSED_LOG_INTERVAL_MILLIS = 10_000L
    }
}

