package com.robining.games.amaplocation

import android.content.Context
import android.content.pm.PackageManager
import com.robining.games.location.api.DiagnosticLocation
import com.robining.games.location.api.LocationDiagnosticSnapshot
import com.robining.games.location.api.LocationDiagnostics
import com.robining.games.location.api.LocationTraceLog
import com.robining.games.location.api.TrajectoryLocationProvider

internal class AmapLocationDiagnostics(
    private val context: Context,
    private val onSnapshot: (LocationDiagnosticSnapshot) -> Unit,
) : LocationDiagnostics {
    private val provider = AmapTrajectoryLocationProvider(context)
    private var updates = 0

    override fun start() {
        val granted = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        onSnapshot(base(running = false, registration = if (granted) "准备请求" else "缺少权限"))
        provider.start(object : TrajectoryLocationProvider.Listener {
            override fun onLocation(sample: com.robining.games.location.api.TrajectoryLocationSample) {
                updates += 1
                onSnapshot(base(running = true, registration = "已注册", live = DiagnosticLocation("AMap", sample.latitude, sample.longitude, sample.accuracyMeters, sample.altitudeMeters, null, 0), updates = updates))
            }
            override fun onError(message: String, cause: Throwable?) {
                onSnapshot(base(running = false, registration = message, updates = updates))
            }
        })
    }

    override fun stop() = provider.stop()
    override fun logPath(): String = LocationTraceLog.path(context).absolutePath

    private fun base(running: Boolean, registration: String, live: DiagnosticLocation? = null, updates: Int = 0) = LocationDiagnosticSnapshot(
        running = running,
        providerStatus = "高德定位 SDK",
        providerPackage = "com.amap.api",
        permissions = if (context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) "精确定位已授权" else "未授予精确定位",
        systemLocation = "由高德 SDK 管理",
        requestSettings = "高精度 / 1 秒 / 无缓存",
        registration = registration,
        availability = if (running) "正在接收" else "等待位置",
        updateCount = updates,
        liveLocation = live,
        events = listOf("坐标在模块边界转换为 WGS-84"),
    )
}
