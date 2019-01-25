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
     * **Not consuming** new actions from [input] will lead to [reduxStore] error state!
     *
     * It is up to implementation of side effect to filter actions
     * and handle only subset of them. If handling the action takes time, better to launch new coroutine for it
     * to not block [reduxStore] new actions processing - consuming should be fast! Also implementation should care
     * about cancelling any previous triggered operations on new input action.
     *
     * @param input a [ReceiveChannel] of actions that comes from [reduxStore]
     * @param stateAccessor provides a way to get current state of [reduxStore]
     * @param output a [SendChannel] of actions that this side effect should use to produce new actions. If side effect
     * doesn't want to handle new action from [input] channel - it could ignore sending action to this [output] channel
     */
    fun CoroutineScope.start(
        input: ReceiveChannel<A>,
        stateAccessor: StateAccessor<S>,
        output: SendChannel<A>
    ) : Job
}

/**
 * Simplified version of [SideEffect] that produces on one input action either one output action or no action.
 *
 * @param S states type
 * @param A actions type
 * @param sideEffect function should check for given `action`/`state` combination and return either [Job]
 * from `handler {}` coroutine function call or `null`, if side effect does not interested
 * in `action` - `state` combination. If `handler {}` coroutine will be called again on new input,
 * while previous invocation still running - old one coroutine will be cancelled.
 */
class SimpleSideEffect<S, A>(
    private val sideEffect: (
        state: StateAccessor<S>,
        action: A,
        handler: (suspend () -> A?) -> Job
    ) -> Job?
) : SideEffect<S, A> {
    override fun CoroutineScope.start(
        input: ReceiveChannel<A>,
        stateAccessor: StateAccessor<S>,
        output: SendChannel<A>
    ) = launch {
        var job: Job? = null
        for (action in input) {
            sideEffect(stateAccessor, action) { handler ->
                job?.cancel()
                launch { handler()?.let { output.send(it) } }
            }?.let { job = it }
        }
    }
}
