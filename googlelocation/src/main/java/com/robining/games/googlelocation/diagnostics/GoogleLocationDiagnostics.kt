package com.robining.games.googlelocation.diagnostics

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import com.robining.games.location.api.DiagnosticLocation
import com.robining.games.location.api.LocationDiagnosticSnapshot
import com.robining.games.location.api.LocationDiagnostics
import com.robining.games.location.api.LocationTraceLog

/** Google Fused diagnostic implementation exposed through the neutral diagnostics contract. */
class GoogleLocationDiagnostics(
    context: Context,
    private val onSnapshot: (LocationDiagnosticSnapshot) -> Unit,
) : LocationDiagnostics {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)
    private val settingsClient = LocationServices.getSettingsClient(appContext)
    private val locationManager = appContext.getSystemService(LocationManager::class.java)
    private var snapshot = LocationDiagnosticSnapshot()
    private var started = false
    private var currentLocationCancellation: CancellationTokenSource? = null
    private var milestoneTasks = emptyList<Runnable>()
    private var lastGnssSummary: String? = null

    private val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MILLIS)
        .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MILLIS)
        .setWaitForAccurateLocation(false)
        .build()

    private val fusedCallback = object : LocationCallback() {
        override fun onLocationAvailability(availability: LocationAvailability) {
            val value = availability.isLocationAvailable
            update { it.copy(availability = value.toString()) }
            event("Fused onLocationAvailability available=$value")
        }

        override fun onLocationResult(result: LocationResult) {
            val locations = result.locations
            locations.forEach { location ->
                update {
                    it.copy(
                        updateCount = it.updateCount + 1,
                        liveLocation = location.toDiagnosticLocation(),
                    )
                }
                event("Fused callback ${location.describe()}")
            }
            if (locations.isEmpty()) event("Fused callback returned an empty batch", warning = true)
        }
    }

    private val nativeListener = LocationListener { location ->
        val diagnostic = location.toDiagnosticLocation()
        when (location.provider) {
            LocationManager.GPS_PROVIDER -> update { it.copy(nativeGpsLocation = diagnostic) }
            LocationManager.NETWORK_PROVIDER -> update { it.copy(nativeNetworkLocation = diagnostic) }
        }
        event("Native callback ${location.describe()}")
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onStarted() = event("GNSS engine started")
        override fun onStopped() = event("GNSS engine stopped", warning = true)
        override fun onFirstFix(ttffMillis: Int) = event("GNSS first fix ttff=${ttffMillis}ms")

        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            for (index in 0 until status.satelliteCount) if (status.usedInFix(index)) used += 1
            val summary = "visible=${status.satelliteCount}, usedInFix=$used"
            update { it.copy(satelliteStatus = summary) }
            if (summary != lastGnssSummary) {
                lastGnssSummary = summary
                event("GNSS satellites $summary")
            }
        }
    }

    override fun logPath(): String = LocationTraceLog.path(appContext).absolutePath

    @SuppressLint("MissingPermission")
    override fun start() {
        if (started) return
        started = true
        val sessionId = SystemClock.elapsedRealtime().toString(16)
        snapshot = LocationDiagnosticSnapshot(sessionId = sessionId, running = true)
        publish()
        event("FUSED_DIAG session=$sessionId START sdk=${Build.VERSION.SDK_INT} device=${Build.MANUFACTURER}/${Build.MODEL}")

        val fine = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        val background = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else true
        update { it.copy(permissions = "fine=$fine, coarse=$coarse, background=$background") }
        event("Permissions fine=$fine coarse=$coarse background=$background")
        if (!fine && !coarse) {
            update { it.copy(registration = "未注册：缺少位置权限") }
            event("Cannot start: location permission missing", warning = true)
            return
        }

        inspectGooglePlayServices()
        inspectSystemLocation()
        checkLocationSettings()
        queryFusedAvailability()
        queryLastLocation()
        queryCurrentLocation()
        requestFusedUpdates()
        requestNativeComparison()
        registerGnssDiagnostics()
        scheduleMilestones()
    }

    override fun stop() {
        if (!started) return
        started = false
        milestoneTasks.forEach(mainHandler::removeCallbacks)
        milestoneTasks = emptyList()
        currentLocationCancellation?.cancel()
        currentLocationCancellation = null
        fusedClient.removeLocationUpdates(fusedCallback)
            .addOnFailureListener { error -> event("Fused remove failed ${error.describeError()}", warning = true) }
        runCatching { locationManager.removeUpdates(nativeListener) }
        runCatching { locationManager.unregisterGnssStatusCallback(gnssCallback) }
        update { it.copy(running = false) }
        event("FUSED_DIAG session=${snapshot.sessionId} STOP updates=${snapshot.updateCount}")
    }

    private fun inspectGooglePlayServices() {
        val availability = GoogleApiAvailability.getInstance()
        val code = availability.isGooglePlayServicesAvailable(appContext)
        val status = "${availability.getErrorString(code)}($code), resolvable=${availability.isUserResolvableError(code)}"
        val packageText = runCatching {
            val info = appContext.packageManager.getPackageInfo(GOOGLE_PLAY_SERVICES_PACKAGE, 0)
            val enabled = info.applicationInfo?.enabled
            "version=${info.versionName}/${info.longVersionCode}, enabled=$enabled"
        }.getOrElse { "package unavailable: ${it.javaClass.simpleName}" }
        update { it.copy(providerStatus = status, providerPackage = packageText) }
        event("Google Play services status=$status package=[$packageText]")
        if (code != ConnectionResult.SUCCESS) event("Google Play services is not available", warning = true)
    }

    private fun inspectSystemLocation() {
        val locationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) locationManager.isLocationEnabled else true
        val providers = runCatching { locationManager.allProviders }.getOrDefault(emptyList())
        val enabledProviders = runCatching { locationManager.getProviders(true) }.getOrDefault(emptyList())
        val wifiScan = runCatching {
            Settings.Global.getInt(appContext.contentResolver, "wifi_scan_always_enabled", -1)
        }.getOrDefault(-1)
        val batteryIgnoring = appContext.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(appContext.packageName)
        val summary = "enabled=$locationEnabled, providers=$providers, active=$enabledProviders, wifiScan=$wifiScan, batteryExempt=$batteryIgnoring"
        update { it.copy(systemLocation = summary) }
        event("System location $summary")
    }

    private fun checkLocationSettings() {
        val settingsRequest = LocationSettingsRequest.Builder().addLocationRequest(request).build()
        settingsClient.checkLocationSettings(settingsRequest)
            .addOnSuccessListener { response ->
                val states = response.locationSettingsStates
                val summary = "OK gps=${states?.isGpsUsable}, network=${states?.isNetworkLocationUsable}, ble=${states?.isBleUsable}"
                update { it.copy(requestSettings = summary) }
                event("SettingsClient $summary")
            }
            .addOnFailureListener { error ->
                val statusCode = (error as? ApiException)?.statusCode
                val resolvable = error is ResolvableApiException
                val summary = "FAILED code=$statusCode resolvable=$resolvable ${error.describeError()}"
                update { it.copy(requestSettings = summary) }
                event("SettingsClient $summary", warning = true)
            }
    }

    @SuppressLint("MissingPermission")
    private fun queryFusedAvailability() {
        fusedClient.locationAvailability
            .addOnSuccessListener { availability ->
                val value = availability?.isLocationAvailable
                update { it.copy(availability = value?.toString() ?: "null") }
                event("Fused getLocationAvailability result=$value")
            }
            .addOnFailureListener { error ->
                update { it.copy(availability = "FAILED ${error.describeError()}") }
                event("Fused getLocationAvailability failed ${error.describeError()}", warning = true)
            }
    }

    @SuppressLint("MissingPermission")
    private fun queryLastLocation() {
        fusedClient.lastLocation
            .addOnSuccessListener { location ->
                update { it.copy(lastLocation = location?.toDiagnosticLocation()) }
                event("Fused lastLocation ${location?.describe() ?: "null"}")
            }
            .addOnFailureListener { error -> event("Fused lastLocation failed ${error.describeError()}", warning = true) }
    }

    @SuppressLint("MissingPermission")
    private fun queryCurrentLocation() {
        val cancellation = CancellationTokenSource()
        currentLocationCancellation = cancellation
        val currentRequest = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(0L)
            .setDurationMillis(CURRENT_LOCATION_TIMEOUT_MILLIS)
            .build()
        fusedClient.getCurrentLocation(currentRequest, cancellation.token)
            .addOnSuccessListener { location ->
                update { it.copy(currentLocation = location?.toDiagnosticLocation()) }
                event("Fused getCurrentLocation ${location?.describe() ?: "null/timeout"}", warning = location == null)
            }
            .addOnFailureListener { error -> event("Fused getCurrentLocation failed ${error.describeError()}", warning = true) }
    }

    @SuppressLint("MissingPermission")
    private fun requestFusedUpdates() {
        update { it.copy(registration = "正在注册") }
        fusedClient.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper())
            .addOnSuccessListener {
                update { it.copy(registration = "注册成功，等待回调") }
                event("Fused requestLocationUpdates SUCCESS")
            }
            .addOnFailureListener { error ->
                update { it.copy(registration = "FAILED ${error.describeError()}") }
                event("Fused requestLocationUpdates FAILED ${error.describeError()}", warning = true)
            }
    }

    @SuppressLint("MissingPermission")
    private fun requestNativeComparison() {
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
            val enabled = runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
            if (!enabled) {
                event("Native provider=$provider disabled", warning = true)
                return@forEach
            }
            runCatching {
                locationManager.requestLocationUpdates(
                    provider,
                    UPDATE_INTERVAL_MILLIS,
                    0f,
                    nativeListener,
                    Looper.getMainLooper(),
                )
            }.onSuccess {
                event("Native request provider=$provider SUCCESS")
            }.onFailure { error ->
                event("Native request provider=$provider FAILED ${error.describeError()}", warning = true)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerGnssDiagnostics() {
        runCatching { locationManager.registerGnssStatusCallback(gnssCallback, mainHandler) }
            .onSuccess { registered -> event("GNSS status registration=$registered", warning = !registered) }
            .onFailure { error -> event("GNSS status registration failed ${error.describeError()}", warning = true) }
    }

    private fun scheduleMilestones() {
        milestoneTasks = MILESTONES_MILLIS.map { elapsed ->
            Runnable {
                if (!started) return@Runnable
                event(
                    "MILESTONE ${elapsed / 1_000}s fusedUpdates=${snapshot.updateCount} " +
                        "availability=${snapshot.availability} nativeGps=${snapshot.nativeGpsLocation != null} " +
                        "nativeNetwork=${snapshot.nativeNetworkLocation != null} gnss=[${snapshot.satelliteStatus}]",
                    warning = snapshot.updateCount == 0,
                )
            }.also { mainHandler.postDelayed(it, elapsed) }
        }
    }

    private fun hasPermission(permission: String): Boolean =
        appContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun update(transform: (LocationDiagnosticSnapshot) -> LocationDiagnosticSnapshot) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            snapshot = transform(snapshot)
            publish()
        } else {
            mainHandler.post { update(transform) }
        }
    }

    private fun event(message: String, warning: Boolean = false) {
        val fullMessage = "FUSED_DIAG session=${snapshot.sessionId} $message"
        if (warning) LocationTraceLog.warn(appContext, fullMessage) else LocationTraceLog.info(appContext, fullMessage)
        val time = LocalTime.now().format(EVENT_TIME_FORMAT)
        update { it.copy(events = (listOf("$time $message") + it.events).take(MAX_UI_EVENTS)) }
    }

    private fun publish() = onSnapshot(snapshot)

    private fun Location.toDiagnosticLocation(): DiagnosticLocation = DiagnosticLocation(
        provider = provider ?: "unknown",
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = takeIf { it.hasAccuracy() }?.accuracy,
        altitudeMeters = takeIf { it.hasAltitude() }?.altitude,
        verticalAccuracyMeters = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasVerticalAccuracy()) {
            verticalAccuracyMeters
        } else null,
        ageMillis = (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos).coerceAtLeast(0L) / 1_000_000L,
    )

    private fun Location.describe(): String =
        "provider=${provider ?: "unknown"} lat=$latitude lng=$longitude " +
            "accuracy=${takeIf { it.hasAccuracy() }?.accuracy} altitude=${takeIf { it.hasAltitude() }?.altitude} " +
            "verticalAccuracy=${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasVerticalAccuracy()) verticalAccuracyMeters else null} " +
            "ageMs=${(SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos).coerceAtLeast(0L) / 1_000_000L} mock=$isMock"

    private fun Throwable.describeError(): String =
        "${javaClass.simpleName}(message=${message}, status=${(this as? ApiException)?.statusCode})"

    private companion object {
        const val GOOGLE_PLAY_SERVICES_PACKAGE = "com.google.android.gms"
        const val UPDATE_INTERVAL_MILLIS = 1_000L
        const val MIN_UPDATE_INTERVAL_MILLIS = 500L
        const val CURRENT_LOCATION_TIMEOUT_MILLIS = 30_000L
        const val MAX_UI_EVENTS = 30
        val MILESTONES_MILLIS = listOf(5_000L, 15_000L, 30_000L, 60_000L)
        val EVENT_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    }
}

