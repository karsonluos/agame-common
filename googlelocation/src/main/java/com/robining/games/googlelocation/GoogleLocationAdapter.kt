package com.robining.games.googlelocation

import android.content.Context
import com.robining.games.location.LocationAdapter
import com.robining.games.location.api.LocationDiagnosticSnapshot
import com.robining.games.location.api.LocationDiagnostics
import com.robining.games.location.api.TrajectoryLocationProvider
import com.robining.games.googlelocation.diagnostics.GoogleLocationDiagnostics
import com.robining.games.googlelocation.hybrid.HybridTrajectoryLocationProvider

class GoogleLocationAdapter : LocationAdapter {
    override val id: String = "google-fused-android-fallback"

    override fun createProvider(context: Context): TrajectoryLocationProvider =
        HybridTrajectoryLocationProvider(context)

    override fun createDiagnostics(
        context: Context,
        onSnapshot: (LocationDiagnosticSnapshot) -> Unit,
    ): LocationDiagnostics = GoogleLocationDiagnostics(context, onSnapshot)
}
