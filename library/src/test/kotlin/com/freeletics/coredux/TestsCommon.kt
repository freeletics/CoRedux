package com.freeletics.coredux

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

internal sealed class State {
    object Initial : State()
    object LoadingItems : State()
}

internal sealed class Actions {
    object LoadItems : Actions()
}

internal class TestStateReceiver<S> : StateReceiver<S> {
    private val stateUpdates = mutableListOf<S>()

    fun receivedStates(count: Int): List<S> {
        val statesCollector = GlobalScope.async {
                while (stateUpdates.size < count) {
                    delay(10)
                }

            stateUpdates.toList()
        }

        return runBlocking { withTimeout(1000) { statesCollector.await() }}
    }

    override fun invoke(newState: S) {
        println("Adding new state: $newState")
        stateUpdates.add(newState)
    }
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
