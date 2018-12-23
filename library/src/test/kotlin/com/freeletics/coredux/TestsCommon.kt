package com.freeletics.coredux

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.runBlocking

internal sealed class State {
    object Initial : State()
    object LoadingItems : State()
}

internal sealed class Actions {
    object LoadItems : Actions()
}

internal fun ReceiveChannel<State>.receive(count: Int): List<State> {
    val output = mutableListOf<State>()
    runBlocking {
        (0 until count).forEach {
            output.add(receive())
        }
    }
    return output.toList()
}
