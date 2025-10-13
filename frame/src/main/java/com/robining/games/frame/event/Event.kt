package com.robining.games.frame.event

interface IEvent<T, R> {
    fun register(receiver: (T) -> R)
    fun unregister(receiver: (T) -> R)
    fun notify(event: T)
    operator fun plusAssign(receiver: (T) -> R) {
        register(receiver)
    }

    operator fun minusAssign(receiver: (T) -> R) {
        unregister(receiver)
    }
}

internal class EventImpl<T, R> : IEvent<T, R> {
    private val set = mutableSetOf<(T) -> R>()
    override fun register(receiver: (T) -> R) {
        set.add(receiver)
    }

    override fun unregister(receiver: (T) -> R) {
        set.remove(receiver)
    }

    override fun notify(event: T) {
        set.forEach {
            it.invoke(event)
        }
    }
}

fun <T, R> event(): IEvent<T, R> {
    return EventImpl()
}