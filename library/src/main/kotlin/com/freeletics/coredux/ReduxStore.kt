package com.freeletics.coredux

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.toChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A [reduxStore] is a Kotlin coroutine based implementation of Redux and redux.js.org.
 *
 * @param initialState The initial state. This one will be emitted directly in onSubscribe()
 * @param stateReceiver function that receives new state updates
 * @param sideEffects The sideEffects. See [SideEffect].
 * @param reducer The reducer.  See [Reducer].
 * @param S The type of the State
 * @param A The type of the Actions
 *
 * @return [ActionDispatcher] that should be use to dispatch new actions to [reduxStore]
 */
@UseExperimental(ExperimentalCoroutinesApi::class, ObsoleteCoroutinesApi::class)
fun <S: Any, A: Any> CoroutineScope.reduxStore(
    initialState: S,
    sideEffects: List<SideEffect<S, A>> = emptyList(),
    reducer: Reducer<S, A>,
    stateReceiver: StateReceiver<S>
): ActionDispatcher<A> {
    val actionsChannel = BroadcastChannel<A>(1 + sideEffects.size)
    var currentState = initialState
    val actionDispatcher: ActionDispatcher<A> = object : ActionDispatcher<A> {
        override fun dispatch(action: A) {
            if (isActive) launch { actionsChannel.send(action) }
        }
    }

//     output.invokeOnClose {
//        this@reduxStore.cancel()
//        actionsChannel.close()
//    }

    // Sending initial state
    stateReceiver(currentState)

    // Starting reducer coroutine
    launch {
        try {
            for (action in actionsChannel.openSubscription()) {
                try {
                    currentState = reducer(currentState, action)
                } catch (e: Throwable) {
                    throw ReducerException(currentState, action, e)
                }
                stateReceiver(currentState)
            }
        } catch (e: Exception) {
            if (!isActive) {
                throw e
            }
        }
//        } finally {
//            if (!output.isClosedForSend) output.close()
//        }
    }

    // Starting side-effects coroutines
    sideEffects.forEach { sideEffect ->
        launch {
            try {
                sideEffect(actionsChannel.openSubscription()) { return@sideEffect currentState }
                    .toChannel(actionsChannel)
            } catch (e: Exception) {
                if (!actionsChannel.isClosedForSend) actionsChannel.close(e)
            }
        }
    }

    return actionDispatcher
}
