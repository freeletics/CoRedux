package com.freeletics.coredux

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals

internal fun stateLengthSE(
    lengthLimit: Int = 10,
    loadDelay: Long = 100L
) = SimpleSideEffect<String, Int> { state, _, handler ->
    val currentStateLength = state().length
    if (currentStateLength <= lengthLimit) {
        handler {
            delay(loadDelay)
            currentStateLength
        }
    } else {
        null
    }
}

class LoggerSE: SideEffect<String, Int> {
    private val recordedActions = mutableListOf<Int>()

    val receivedActions get() = recordedActions.toList()

    override fun CoroutineScope.start(
        input: ReceiveChannel<Int>,
        stateAccessor: StateAccessor<String>,
        output: SendChannel<Int>
    ) = launch {
        for (action in input) {
            recordedActions.add(action)
        }
    }
}

internal fun multiplyActionSE(
    produceDelay: Long = 100L
) = CancellableSideEffect<String, Int> { _, action, handler ->
    when (action) {
        in 100..1000 -> handler { output ->
            launch {
                delay(produceDelay)
                output.send(action * 20)
            }
        }
        else -> null
    }
}

internal class TestStateReceiver<S> : StateReceiver<S> {
    private val zeroTime = System.currentTimeMillis()
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

    fun assertStates(vararg expectedStates: S) {
        val collectedStates = receivedStates(expectedStates.size)
        assertEquals(expectedStates.toList(), collectedStates)
    }

    override fun invoke(newState: S) {
        println("+$timeDiff ms - Adding new state: $newState")
        stateUpdates.add(newState)
    }

    private val timeDiff get() = System.currentTimeMillis() - zeroTime
}

internal class TestStateAccessor<S>(
    private var currentState: S
) : StateAccessor<S> {
    fun setCurrentState(state: S) { currentState = state }

    override fun invoke(): S = currentState
}
