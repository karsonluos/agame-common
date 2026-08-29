package com.robining.games.location.api

data class DiagnosticLocation(
    val provider: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val altitudeMeters: Double?,
    val verticalAccuracyMeters: Float?,
    val ageMillis: Long,
)

/** Fields are intentionally provider-neutral so another adapter can report its own diagnostics. */
data class LocationDiagnosticSnapshot(
    val sessionId: String = "--",
    val running: Boolean = false,
    val providerStatus: String = "等待检查",
    val providerPackage: String = "等待检查",
    val permissions: String = "等待检查",
    val systemLocation: String = "等待检查",
    val requestSettings: String = "等待检查",
    val registration: String = "尚未请求",
    val availability: String = "尚未返回",
    val updateCount: Int = 0,
    val liveLocation: DiagnosticLocation? = null,
    val currentLocation: DiagnosticLocation? = null,
    val lastLocation: DiagnosticLocation? = null,
    val nativeGpsLocation: DiagnosticLocation? = null,
    val nativeNetworkLocation: DiagnosticLocation? = null,
    val satelliteStatus: String = "尚未返回",
    val events: List<String> = emptyList(),
)

interface LocationDiagnostics {
    fun start()
    fun stop()
    fun logPath(): String
}
