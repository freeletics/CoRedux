package com.freeletics.coredux

/**
 * Implementation will receive new state updates.
 */
typealias StateReceiver<S> = (S) -> Unit
