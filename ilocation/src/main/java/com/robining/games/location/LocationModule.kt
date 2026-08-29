package com.robining.games.location

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.ServiceLoader
import kotlinx.coroutines.suspendCancellableCoroutine
import com.robining.games.location.api.LocationDiagnosticSnapshot
import com.robining.games.location.api.LocationDiagnostics
import com.robining.games.location.api.TrajectoryLocationProvider
import com.robining.games.location.api.TrajectoryLocationSample
import kotlin.coroutines.resume

/** Vendor-neutral location composition root. */
object LocationModule {
    private val adapters: Map<String, LocationAdapter> by lazy {
        val adapters = ServiceLoader
            .load(LocationAdapter::class.java, LocationAdapter::class.java.classLoader)
            .toList()
        require(adapters.map(LocationAdapter::id).distinct().size == adapters.size) {
            "Duplicate LocationAdapter ids: ${adapters.map(LocationAdapter::id)}"
        }
        adapters.associateBy(LocationAdapter::id)
    }

    @Volatile
    private var selectedAdapterId: String? = null

    /** Select an implementation explicitly during Application.onCreate(). */
    fun select(adapterId: String) {
        require(adapterId in adapters) {
            "Unknown LocationAdapter '$adapterId'. Available: ${adapters.keys.sorted()}"
        }
        selectedAdapterId = adapterId
    }

    val availableAdapterIds: Set<String> get() = adapters.keys

    private val adapter: LocationAdapter
        get() {
            val id = selectedAdapterId
                ?: error("No LocationAdapter selected. Call LocationModule.select(...) during Application.onCreate(). Available: ${adapters.keys.sorted()}")
            return checkNotNull(adapters[id])
        }

    val adapterId: String get() = adapter.id

    fun createProvider(context: Context): TrajectoryLocationProvider =
        adapter.createProvider(context.applicationContext)

    fun createDiagnostics(
        context: Context,
        onSnapshot: (LocationDiagnosticSnapshot) -> Unit,
    ): LocationDiagnostics = adapter.createDiagnostics(context.applicationContext, onSnapshot)
        ?: UnsupportedLocationDiagnostics(onSnapshot)

    suspend fun currentLocation(context: Context, timeoutMillis: Long = 10_000L): TrajectoryLocationSample? =
        suspendCancellableCoroutine { continuation ->
            val provider = createProvider(context)
            val handler = Handler(Looper.getMainLooper())
            var completed = false

            fun finish(sample: TrajectoryLocationSample?) {
                if (completed) return
                completed = true
                handler.removeCallbacksAndMessages(provider)
                provider.stop()
                if (continuation.isActive) continuation.resume(sample)
            }

            val timeout = Runnable { finish(null) }
            val started = provider.start(object : TrajectoryLocationProvider.Listener {
                override fun onLocation(sample: TrajectoryLocationSample) = finish(sample)
                override fun onError(message: String, cause: Throwable?) = finish(null)
            })
            if (started) handler.postAtTime(timeout, provider, android.os.SystemClock.uptimeMillis() + timeoutMillis)
            else finish(null)
            continuation.invokeOnCancellation {
                handler.removeCallbacksAndMessages(provider)
                provider.stop()
            }
        }

    private class UnsupportedLocationDiagnostics(
        private val onSnapshot: (LocationDiagnosticSnapshot) -> Unit,
    ) : LocationDiagnostics {
        override fun start() = onSnapshot(
            LocationDiagnosticSnapshot(providerStatus = "当前定位实现不支持诊断"),
        )
        override fun stop() = Unit
        override fun logPath(): String = "--"
    }
}
