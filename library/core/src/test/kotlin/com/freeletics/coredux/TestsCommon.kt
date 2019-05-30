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
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals

internal fun stateLengthSE(
    lengthLimit: Int = 10,
    loadDelay: Long = 100L
) = SimpleSideEffect<String, Int>("stateLengthSE") { state, _, _, handler ->
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
    override val name: String = "LoggerSE"

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

internal fun multiplyActionSE(
    produceDelay: Long = 100L
) = CancellableSideEffect<String, Int>("multiplyActionSE") { _, action, _, handler ->
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

internal class TestStateReceiver<S>() : StateReceiver<S> {
    private val _stateUpdates = mutableListOf<S>()
    val stateUpdates get() = _stateUpdates.toList()

    fun assertStates(vararg expectedStates: S) {
        val currentStateUpdates = stateUpdates
        if (currentStateUpdates.size < expectedStates.size) {
            throw IllegalStateException(
                "Expected ${expectedStates.joinToString(prefix = "[", postfix = "]")} states, " +
                "but actually received $currentStateUpdates"
            )
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
internal class TestLogger : LogSink {
    private val testScope = TestCoroutineScope()
    private val logEvents = mutableListOf<LogEntry>()

    val receivedLogEntries get() = logEvents.toList()

    fun close() {
        testScope.cleanupTestCoroutines()
    }

    fun waitForLogEntries(count: Int) = testScope.runBlockingTest {
        withTimeout(1000) {
            while (logEvents.size < count) {
                delay(10)
            }
            logEvents.toList()
        }
    }

    fun assertLogEvents(vararg expected: LogEvent) {
        val receivedLogEvents = receivedLogEntries.subList(0, expected.size)
        expected.forEachIndexed { index, logEntry ->
            assertEquals(logEntry, receivedLogEvents[index].event)
        }
    }

    override val sink: SendChannel<LogEntry> = testScope.actor {
        while(true) {
            logEvents.add(receive())
        }
    }
}
