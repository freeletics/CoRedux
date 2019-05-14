package com.freeletics.coredux.dsl

import com.freeletics.coredux.LogSink
import com.freeletics.coredux.Store
import com.freeletics.coredux.createStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart

fun <S : Any, A : Any> CoroutineScope.describeStore(
    storeName: String,
    builder: StoreBuilder<S, A>.() -> Unit
): Store<S, A> {
    val storeBuilder = StoreBuilder<S, A>(storeName, this)
    storeBuilder.builder()
    return storeBuilder.createStore()
}

class StoreBuilder<S : Any, A : Any>(
    private val storeName: String,
    private val coroutineScope: CoroutineScope
) {
    private var logSinks: List<LogSink> = emptyList()
    private var startMode: CoroutineStart = CoroutineStart.LAZY
    private val reducerBuilder = ReducerBuilder<S, A>()
    private val sideEffectsBuilder = SideEffectsBuilder<S, A>()

    internal fun createStore(): Store<S, A> {
        return coroutineScope.createStore(
            name = storeName,
            initialState = reducerBuilder.initialState,
            sideEffects = sideEffectsBuilder.build(),
            launchMode = startMode,
            logSinks = logSinks,
            reducer = reducerBuilder.build()
        )
    }

    fun enableLogging(logSinks: List<LogSink>) {
        this.logSinks = logSinks
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
