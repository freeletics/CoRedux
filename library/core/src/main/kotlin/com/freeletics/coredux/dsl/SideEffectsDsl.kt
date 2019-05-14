package com.freeletics.coredux.dsl

import com.freeletics.coredux.SideEffect

class SideEffectsBuilder<S : Any, A : Any> {
    internal fun build(): List<SideEffect<S, A>> = emptyList()
}
