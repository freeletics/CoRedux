package com.freeletics.coredux

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.toChannel
import kotlinx.coroutines.launch

/**
 * A ReduxStore is a RxJava based implementation of Redux and redux-observable.js.org.
 * A ReduxStore takes Actions from upstream as input events.
 * [SideEffect]s can be registered to listen for a certain
 * Action to react on a that Action as a (impure) side effect and create yet another Action as
 * output. Every Action goes through the a [Reducer], which is basically a pure function that takes
 * the current State and an Action to compute a new State.
 * The new state will be emitted downstream to any listener interested in it.
 *
 * A ReduxStore observable never reaches onComplete(). If a error occurs in the [Reducer] or in any
 * side effect ([Throwable] has been thrown) then the ReduxStore reaches onError() as well and
 * according to the reactive stream specs the store cannot recover the error state.
 *
 * @param initialState The initial state. This one will be emitted directly in onSubscribe()
 * @param sideEffects The sideEffects. See [SideEffect]
 * @param reducer The reducer.  See [Reducer].
 * @param S The type of the State
 * @param A The type of the Actions
 */
@UseExperimental(ExperimentalCoroutinesApi::class, ObsoleteCoroutinesApi::class)
fun <S: Any, A: Any> ReceiveChannel<A>.reduxStore(
    initialState: S,
    sideEffects: List<SideEffect<S, A>>,
    coroutineScope: CoroutineScope = GlobalScope,
    reducer: Reducer<S, A>
): ReceiveChannel<S> {
    val output = Channel<S>()
    val actionsChannel = BroadcastChannel<A>(1 + sideEffects.size)
    var currentState = initialState

    output.invokeOnClose {
        this@reduxStore.cancel()
        actionsChannel.close()
    }

    // Sending initial state
    coroutineScope.launch {
        output.send(currentState)
    }

    // Starting reducer coroutine
    coroutineScope.launch {
        try {
            for (action in actionsChannel.openSubscription()) {
                try {
                    currentState = reducer(currentState, action)
                } catch (e: Throwable) {
                    output.close(ReducerException(currentState, action, e))
                }
                coroutineScope.launch {
                    output.send(currentState)
                }
            }
        } catch (e: Exception) {
            if (!output.isClosedForSend) {
                output.close(e)
            }
        } finally {
            if (!output.isClosedForSend) output.close()
        }
    }

    // Starting side-effects coroutines
    sideEffects.forEach { sideEffect ->
        coroutineScope.launch {
            try {
                sideEffect(actionsChannel.openSubscription()) { return@sideEffect currentState }
                    .toChannel(actionsChannel)
            } catch (e: Exception) {
                if (!actionsChannel.isClosedForSend) actionsChannel.close(e)
            }
        }
    }

    // Start receiving items from upstream
    coroutineScope.launch {
        try {
            this@reduxStore.toChannel(actionsChannel)
        } catch (e: Exception) {
            if (!actionsChannel.isClosedForSend) actionsChannel.close(e)
        } finally {
            if (!actionsChannel.isClosedForSend) actionsChannel.close()
        }
    }

    return output
}

/**
 * Just a convenience method to use varags for arbitarry many sideeffects instead a list of SideEffects.
 * See [reduxStore] documentation.
 *
 * @see reduxStore
 */
fun <S: Any, A: Any> ReceiveChannel<A>.reduxStore(
    initialState: S,
    vararg sideEffects: SideEffect<S, A>,
    coroutineScope: CoroutineScope = GlobalScope,
    reducer: Reducer<S, A>
): ReceiveChannel<S> = reduxStore(
    initialState = initialState,
    sideEffects = sideEffects.toList(),
    reducer = reducer,
    coroutineScope = coroutineScope
)
