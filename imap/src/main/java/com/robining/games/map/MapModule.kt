package com.robining.games.map

import java.util.ServiceLoader

object MapModule {
    private val adapters: Map<String, MapAdapter> by lazy {
        val adapters = ServiceLoader
            .load(MapAdapter::class.java, MapAdapter::class.java.classLoader)
            .toList()
        require(adapters.map(MapAdapter::id).distinct().size == adapters.size) {
            "Duplicate MapAdapter ids: ${adapters.map(MapAdapter::id)}"
        }
        adapters.associateBy(MapAdapter::id)
    }

    @Volatile
    private var selectedAdapterId: String? = null

    /** Select an implementation explicitly during Application.onCreate(). */
    fun select(adapterId: String) {
        require(adapterId in adapters) {
            "Unknown MapAdapter '$adapterId'. Available: ${adapters.keys.sorted()}"
        }
        selectedAdapterId = adapterId
    }

    val availableAdapterIds: Set<String> get() = adapters.keys

    internal val adapter: MapAdapter
        get() {
            val id = selectedAdapterId
                ?: error("No MapAdapter selected. Call MapModule.select(...) during Application.onCreate(). Available: ${adapters.keys.sorted()}")
            return checkNotNull(adapters[id])
        }

    val adapterId: String get() = adapter.id
}
