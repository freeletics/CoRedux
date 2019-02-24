package com.freeletics.coredux

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch

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
