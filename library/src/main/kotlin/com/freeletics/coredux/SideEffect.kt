package com.freeletics.coredux

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch

/**
 * Returns a __current__ state of [reduxStore].
 */
typealias StateAccessor<S> = () -> S

/**
 * [reduxStore] side effect that handles cases when action may not update state
 * or it may take time to do it.
 *
 * @param S states type
 * @param A actions type
 */
interface SideEffect<S, A> {
    /**
     * Starts side effect [Job] in given [CoroutineScope].
     *
     * It is up to implementation of side effect to filter actions
     * and handle only subset of them.
     *
     * Also implementation should care about cancelling any previous triggered
     * operations on new input action.
     *
     * @param input a [ReceiveChannel] of actions that comes from [reduxStore]
     * @param stateAccessor provides a way to get current state of [reduxStore]
     * @param output a [SendChannel] of actions that this side effect should use to produce new actions. nIf side effect
     * doesn't want to handle new action from [input] channel - it could ignore sending action to this [output] channel
     */
    fun CoroutineScope.start(
        input: ReceiveChannel<A>,
        stateAccessor: StateAccessor<S>,
        output: SendChannel<A>
    ) : Job
}

/**
 * Simplified implementation of [SideEffect] that produces on one input action either one output action or no action.
 *
 * On each new combinations of input action and current state that produces `true` in [shouldHandleNewAction] method,
 * previous invocation of [handleAction] will be cancelled
 * and [handleAction] will be called again with new inputs.
 *
 * @param S states type
 * @param A actions type
 */
abstract class SimpleSideEffect<S, A> : SideEffect<S, A> {
    override fun CoroutineScope.start(
        input: ReceiveChannel<A>,
        stateAccessor: StateAccessor<S>,
        output: SendChannel<A>
    ) = launch {
        var job: Job? = null
        for (action in input) {
            val state = stateAccessor()
            if (shouldHandleNewAction(action, state)) {
                job?.cancel()
                job = launch {
                    handleAction(action, state)?.let { output.send(it) }
                }
            }
        }
    }

    /**
     * Only given combinations of [inputAction] and [currentState] that produces `true` will be passed
     * to [handleAction] method call.
     */
    abstract fun shouldHandleNewAction(
        inputAction: A,
        currentState: S
    ): Boolean

    /**
     * Handles [inputAction] from [reduxStore] and may produce new output action.
     *
     * @param inputAction new input action that was passed to [shouldHandleNewAction]
     * @param currentState current state that was passed to [shouldHandleNewAction]
     * @return either new action or `null` if this side effect doesn't want to produce new action
     */
    abstract suspend fun handleAction(
        inputAction: A,
        currentState: S
    ): A?
}
