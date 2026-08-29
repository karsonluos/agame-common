package com.robining.games.location

import android.content.Context
import com.robining.games.location.api.LocationDiagnosticSnapshot
import com.robining.games.location.api.LocationDiagnostics
import com.robining.games.location.api.TrajectoryLocationProvider

/** Implemented by a concrete location-source module and discovered at runtime. */
interface LocationAdapter {
    val id: String
    fun createProvider(context: Context): TrajectoryLocationProvider
    fun createDiagnostics(
        context: Context,
        onSnapshot: (LocationDiagnosticSnapshot) -> Unit,
    ): LocationDiagnostics?
}
