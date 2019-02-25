package com.freeletics.coredux

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel

/**
 * Returns a __current__ state of [createStore].
 */
typealias StateAccessor<S> = () -> S

/**
 * [createStore] side effect that handles cases when action may not update state
 * or it may take time to do it.
 *
 * @param S states type
 * @param A actions type
 */
interface SideEffect<S : Any, A : Any> {
    /**
     * Preferably _unique per store instance_ name for side effect.
     */
    val name: String

    /**
     * Starts side effect [Job] in given [CoroutineScope].
     *
     * **Not consuming** new actions from [input] will lead to [createStore] error state!
     *
     * It is up to implementation of side effect to filter actions
     * and handle only subset of them. If handling the action takes time, better to launch new coroutine for it
     * to not block [createStore] new actions processing - consuming should be fast! Also implementation should care
     * about cancelling any previous triggered operations on new input action.
     *
     * @param input a [ReceiveChannel] of actions that comes from [createStore]
     * @param stateAccessor provides a way to get current state of [createStore]
     * @param output a [SendChannel] of actions that this side effect should use to produce new actions. If side effect
     * doesn't want to handle new action from [input] channel - it could ignore sending action to this [output] channel
     * @param logger [SideEffectLogger] instance that should be used to log events
     */
    fun CoroutineScope.start(
        input: ReceiveChannel<A>,
        stateAccessor: StateAccessor<S>,
        output: SendChannel<A>,
        logger: SideEffectLogger
    ) : Job
}
