package com.robining.games.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun rememberAppMapController(initialCamera: MapCamera = MapCamera()): AppMapController =
    MapModule.adapter.rememberController(initialCamera)

@Composable
fun AppMap(
    modifier: Modifier = Modifier,
    mapName: String,
    controller: AppMapController,
    appearance: MapAppearance,
    route: MapRoute? = null,
    markers: List<MapMarker> = emptyList(),
    onMapClick: ((MapCoordinate) -> Boolean)? = null,
) = MapModule.adapter.MapCanvas(
    modifier = modifier,
    mapName = mapName,
    controller = controller,
    appearance = appearance,
    route = route,
    markers = markers,
    onMapClick = onMapClick,
)
