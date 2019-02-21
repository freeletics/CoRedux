package com.freeletics.coredux

/**
 * Filters [StateReceiver] by only passing states that are distinct from their predecessor state using [isStateDistinct]
 * function.
 *
 * @param [isStateDistinct] receives `previousState` (can be null initially), `newState` and outputs `true` if new state
 * should be passed to [StateReceiver] or `false` if not.
 */
fun <S : Any> StateReceiver<S>.distinctUntilChangedBy(
    isStateDistinct: (previousState: S?, newState: S) -> Boolean
): StateReceiver<S> {
    var previousState: S? = null

    return {
        if (isStateDistinct(previousState, it)) {
            previousState = it
            this(it)
        }
    }
}

/**
 * Filters [StateReceiver] by only passing states that are distinct from their predecessor state using states equality,
 * only non-equal states will pass.
 */
fun <S : Any> StateReceiver<S>.distinctUntilChanged(): StateReceiver<S> =
    distinctUntilChangedBy { previousState, newState ->
        previousState != newState
    }

/**
 * Subscribe [stateReceiver] to [distinctUntilChanged] state updates on [Store].
 */
fun <S : Any, A : Any> Store<S, A>.subscribeToChangedStateUpdates(stateReceiver: StateReceiver<S>) {
    subscribe(stateReceiver.distinctUntilChanged())
}
