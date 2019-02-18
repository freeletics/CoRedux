package com.freeletics.coredux

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch

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

/**
 * Simplified version of [SideEffect] that produces on one input action either one output action or no action.
 *
 * @param S states type
 * @param A actions type
 * @param name unique per store instance side effect name
 * @param sideEffect function should check for given `action`/`state` combination and return either [Job]
 * from `handler {}` coroutine function call or `null`, if side effect does not interested
 * in `action` - `state` combination. If `handler {}` coroutine will be called again on new input,
 * while previous invocation still running - old one coroutine will be cancelled.
 */
class SimpleSideEffect<S : Any, A : Any>(
    override val name: String,
    private val sideEffect: (
        state: StateAccessor<S>,
        action: A,
        logger: SideEffectLogger,
        handler: (suspend () -> A?) -> Job
    ) -> Job?
) : SideEffect<S, A> {

    override fun CoroutineScope.start(
        input: ReceiveChannel<A>,
        stateAccessor: StateAccessor<S>,
        output: SendChannel<A>,
        logger: SideEffectLogger
    ) = launch(context = CoroutineName(name)) {
        var job: Job? = null
        for (action in input) {
            logger.logSideEffectEvent { LogEvent.SideEffectEvent.InputAction(name, action) }
            sideEffect(stateAccessor, action, logger) { handler ->
                job?.run {
                    if (isActive) {
                        logger.logSideEffectEvent {
                            LogEvent.SideEffectEvent.Custom(
                                name,
                                "Cancelling previous job on new $action action"
                            )
                        }
                    }
                    cancel()
                }
                launch { handler()?.let {
                    logger.logSideEffectEvent { LogEvent.SideEffectEvent.DispatchingToReducer(name, it) }
                    output.send(it)
                } }
            }?.let { job = it }
        }
    }
}

/**
 * Version of [SideEffect], that cancels currently executed [Job] and starts a new one.
 *
 * @param S state type
 * @param A action type
 * @param name unique per store instance side effect name
 * @param sideEffect function should check for given `action`/`state` combination and return either [Job] from
 * `handler {}` function call or `null`, if side effect does not interested in `action`/`state` combination.
 * If `handler {}` function will be called again on new input, while previous returned [Job] is still running -
 * old one [Job] will be cancelled.
 */
class CancellableSideEffect<S : Any, A : Any>(
    override val name: String,
    private val sideEffect: (
        state: StateAccessor<S>,
        action: A,
        logger: SideEffectLogger,
        handler: (CoroutineScope.(SendChannel<A>) -> Unit) -> Job
    ) -> Job?
) : SideEffect<S, A> {

    override fun CoroutineScope.start(
        input: ReceiveChannel<A>,
        stateAccessor: StateAccessor<S>,
        output: SendChannel<A>,
        logger: SideEffectLogger
    ): Job = launch(context = CoroutineName(name)) {
        var job: Job? = null
        for (action in input) {
            logger.logSideEffectEvent { LogEvent.SideEffectEvent.InputAction(name, action) }
            sideEffect(stateAccessor, action, logger) { handler ->
                job?.run {
                    if (isActive) {
                        logger.logSideEffectEvent {
                            LogEvent.SideEffectEvent.Custom(
                                name,
                                "Cancelling previous job on new $action action"
                            )
                        }
                    }
                    cancel()
                }
                launch { handler(output) }
            }?.let { job = it }
        }
    }
}
