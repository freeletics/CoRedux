package com.freeletics.coredux

/**
 * Dispatches new actions to [reduxStore] instance.
 *
 * Should be returned by [reduxStore].
 */
interface ActionDispatcher<A> {
    fun dispatch(action: A)
}
