package com.freeletics.coredux.dsl

import com.freeletics.coredux.LogSink
import com.freeletics.coredux.Store
import com.freeletics.coredux.createStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart

fun <S : Any, A : Any> CoroutineScope.describeStore(
    builder: StoreBuilder<S, A>.() -> Unit
): Store<S, A> {
    val storeBuilder = StoreBuilder<S, A>(this)
    storeBuilder.builder()
    return storeBuilder.createStore()
}

class StoreBuilder<S : Any, A : Any>(
    private val coroutineScope: CoroutineScope
) {
    private var logSinks: MutableList<LogSink> = mutableListOf()
    private var startMode: CoroutineStart = CoroutineStart.LAZY
    private var storeName: String? = null
    private val reducerBuilder = ReducerBuilder<S, A>()
    private val sideEffectsBuilder = SideEffectsBuilder<S, A>()

    internal fun createStore(): Store<S, A> {
        val name = requireNotNull(storeName)

        return coroutineScope.createStore(
            name = name,
            initialState = reducerBuilder.initialState,
            sideEffects = sideEffectsBuilder.build(),
            launchMode = startMode,
            logSinks = logSinks.toList(),
            reducer = reducerBuilder.build()
        )
    }

    fun name(storeName: String) {
        this.storeName = storeName
    }

    fun withLogger(logSink: LogSink) {
        this.logSinks.add(logSink)
    }

    operator fun LogSink.unaryPlus() {
        withLogger(this)
    }

    fun startImmediately() {
        startMode = CoroutineStart.DEFAULT
    }

    fun reducer(builder: ReducerBuilder<S, A>.() -> Unit) {
        reducerBuilder.builder()
    }

    fun sideEffects(builder: SideEffectsBuilder<S, A>.() -> Unit) {
        sideEffectsBuilder.builder()
    }
}
