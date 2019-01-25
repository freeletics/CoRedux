package com.freeletics.coredux

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
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
@UseExperimental(ExperimentalCoroutinesApi::class)
fun <S: Any, A: Any> CoroutineScope.reduxStore(
    initialState: S,
    stateReceiver: StateReceiver<S>,
    sideEffects: List<SideEffect<S, A>> = emptyList(),
    reducer: Reducer<S, A>
): ActionDispatcher<A> {
    val actionsReducerChannel = Channel<A>(Channel.UNLIMITED)
    val actionsSideEffectsChannel = BroadcastChannel<A>(CONFLATED)

    val actionDispatcher: ActionDispatcher<A> = { action ->
        if (actionsReducerChannel.isClosedForSend) throw IllegalStateException("CoroutineScope is cancelled")

        if (!actionsReducerChannel.offer(action)) {
            throw IllegalStateException("Input actions overflow - buffer is full")
        }
    }

    coroutineContext[Job]?.invokeOnCompletion {
        actionsReducerChannel.close(it)
        actionsSideEffectsChannel.close(it)
    }

    // Starting reducer coroutine
    launch {
        var currentState = initialState

        // Sending initial state
        stateReceiver(currentState)

        // Starting side-effects coroutines
        sideEffects.forEach { sideEffect ->
            with (sideEffect) {
                start(
                    actionsSideEffectsChannel.openSubscription(),
                    { currentState },
                    actionsReducerChannel
                )
            }
        }

        try {
            for (action in actionsReducerChannel) {
                val newState = try {
                    reducer(currentState, action)
                } catch (e: Throwable) {
                    throw ReducerException(currentState, action, e)
                }

                if (newState != null) {
                    currentState = newState
                    stateReceiver(currentState)
                }

                actionsSideEffectsChannel.send(action)
            }
        } finally {
            if (isActive) cancel()
        }
    }

    return actionDispatcher
}

/**
 * Dispatches new actions to given [reduxStore] instance.
 *
 * It is safe to call this method from different threads,
 * action will consumed on [reduxStore] [CoroutineScope] context.
 */
typealias ActionDispatcher<A> = (A) -> Unit

/**
 * A simple type alias for a reducer function.
 * A Reducer takes a State and an Action as input and produces a state as output.
 *
 * If a reducer should not react on a Action, just return `null`.
 *
 * @param S The type of the state
 * @param A The type of the Actions
 */
typealias Reducer<S, A> = (S, A) -> S?

/**
 * Wraps [Reducer] call exception.
 */
class ReducerException(
    state: Any,
    action: Any,
    cause: Throwable
) : RuntimeException("Exception was thrown by reducer, state = '$state', action = '$action'", cause)

/**
 * Type alias for a updated state receiver function.
 *
 * State update will always be received on [reduxStore] [CoroutineScope] thread.
 */
typealias StateReceiver<S> = (S) -> Unit
