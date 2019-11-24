package com.freeletics.coredux

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.Assert.assertEquals

internal const val STATE_LENGTH_SE_NAME = "stateLengthSE"

internal fun stateLengthSE(
    lengthLimit: Int = 10,
    loadDelay: Long = 100L
) = SimpleSideEffect<String, Int>(STATE_LENGTH_SE_NAME) { state, _, _, handler ->
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

internal const val LOGGER_SE_NAME = "LoggerSE"

internal class LoggerSE: SideEffect<String, Int> {
    override val name: String = LOGGER_SE_NAME

    private val recordedActions = mutableListOf<Int>()

    val receivedActions get() = recordedActions.toList()

    override fun CoroutineScope.start(
        input: ReceiveChannel<Int>,
        stateAccessor: StateAccessor<String>,
        output: SendChannel<Int>,
        logger: SideEffectLogger
    ) = launch {
        for (action in input) {
            recordedActions.add(action)
        }
    }
}

internal const val MULTIPLY_ACTION_SE_NAME = "multiplyActionSE"

internal fun multiplyActionSE(
    produceDelay: Long = 100L
) = CancellableSideEffect<String, Int>(MULTIPLY_ACTION_SE_NAME) { _, action, _, handler ->
    when (action) {
        in 100..1000 -> handler { _, output ->
            launch {
                delay(produceDelay)
                output.send(action * 20)
            }
        }
        else -> null
    }
}

internal class TestStateReceiver<S> : StateReceiver<S> {
    private val _stateUpdates = mutableListOf<S>()
    val stateUpdates get() = _stateUpdates.toList()

    fun assertStates(vararg expectedStates: S) {
        val currentStateUpdates = stateUpdates
        require (expectedStates.size <= currentStateUpdates.size) {
            "Expected ${expectedStates.joinToString(prefix = "[", postfix = "]")} states, " +
                "but actually received $currentStateUpdates"
        }
        val collectedStates = stateUpdates.subList(0, expectedStates.size)
        assertEquals(expectedStates.toList(), collectedStates)
    }

    override fun invoke(newState: S) {
        _stateUpdates.add(newState)
    }
}

internal class TestStateAccessor<S>(
    private var currentState: S
) : StateAccessor<S> {
    fun setCurrentState(state: S) { currentState = state }

    override fun invoke(): S = currentState
}

internal class StubSideEffectLogger : SideEffectLogger {
    override fun logSideEffectEvent(event: () -> LogEvent.SideEffectEvent) = Unit
}

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
internal class TestLogger(
    testScope: TestCoroutineScope
) : LogSink {
    private val _logEvents = mutableListOf<LogEntry>()
    val logEvents get() = _logEvents.toList()

    fun assertLogEvents(vararg expected: LogEvent) {
        val receivedLogEvents = logEvents
        require (expected.size <= receivedLogEvents.size) {
            "Expected ${expected.joinToString(prefix = "[", postfix = "]")} log events, " +
                "but actually received $receivedLogEvents"
        }
        expected.forEachIndexed { index, logEntry ->
            assertEquals(logEntry, receivedLogEvents[index].event)
        }
    }

    override val sink: SendChannel<LogEntry> = testScope.actor {
        while (true) {
            _logEvents.add(receive())
        }
    }
}
