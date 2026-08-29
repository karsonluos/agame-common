package com.robining.games.location.api

/** Vendor-neutral location source used by application features. */
interface TrajectoryLocationProvider {
    val id: String
    val displayName: String

    /** Returns true only when a device location subscription was requested. */
    fun start(listener: Listener): Boolean
    fun stop()

    interface Listener {
        fun onLocation(sample: TrajectoryLocationSample)
        fun onBearing(degrees: Double) = Unit
        fun onError(message: String, cause: Throwable? = null)
    }
}

/** SDK-neutral location value shared by recording, weather and map features. */
data class TrajectoryLocationSample(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val accuracyMeters: Float?,
    val recordedAtMillis: Long,
    val elapsedRealtimeNanos: Long,
    val speedMetersPerSecond: Float?,
    val speedAccuracyMetersPerSecond: Float?,
    val motionProcessed: Boolean = false,
)

