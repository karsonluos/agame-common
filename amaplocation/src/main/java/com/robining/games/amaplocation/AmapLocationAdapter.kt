package com.robining.games.amaplocation

import android.content.Context
import com.amap.api.location.AMapLocationClient
import com.robining.games.location.LocationAdapter
import com.robining.games.location.api.LocationDiagnosticSnapshot
import com.robining.games.location.api.LocationDiagnostics
import com.robining.games.location.api.TrajectoryLocationProvider

class AmapLocationAdapter : LocationAdapter {
    override val id: String = "amap-location"

    override fun createProvider(context: Context): TrajectoryLocationProvider {
        initializePrivacy(context)
        return AmapTrajectoryLocationProvider(context)
    }

    override fun createDiagnostics(
        context: Context,
        onSnapshot: (LocationDiagnosticSnapshot) -> Unit,
    ): LocationDiagnostics {
        initializePrivacy(context)
        return AmapLocationDiagnostics(context, onSnapshot)
    }

    private fun initializePrivacy(context: Context) {
        val appContext = context.applicationContext
        AMapLocationClient.updatePrivacyShow(appContext, true, true)
        AMapLocationClient.updatePrivacyAgree(appContext, true)
    }
}
