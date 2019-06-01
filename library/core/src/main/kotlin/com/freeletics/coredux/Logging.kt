package com.freeletics.coredux

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch

/**
 * Provides a logging interface to [SideEffect] implementations.
 *
 * At least, every [SideEffect] implementation should add logging for [LogEvent.SideEffectEvent.Start],
 * [LogEvent.SideEffectEvent.InputAction] and [LogEvent.SideEffectEvent.DispatchingToReducer] events.
 */
interface SideEffectLogger {
    /**
     * Log [LogEvent.SideEffectEvent].
     */
    fun logSideEffectEvent(event: () -> LogEvent.SideEffectEvent)
}

/**
 * Receiver of log events from store instance.
 *
 * Receiving should be fast. Sender has __small__ internal buffer
 * and on buffer overflow new [LogEntry]s will be dropped
 * until [LogEntry]s from buffer will be consumed by all [LogSink] instances.
 *
 * May be shared between different store instances.
 */
interface LogSink {
    /**
     * [SendChannel] sink to receive log events.
     */
    val sink: SendChannel<LogEntry>
}

/**
 * Single log entity.
 *
 * @param storeName "unique" store name that was passed as `name` param to [createStore]
 * @param time [unixtime](https://en.wikipedia.org/wiki/Unix_time) in milliseconds when log event happened
 * @param event actual event
 */
data class LogEntry(
    val storeName: String,
    val time: Long,
    val event: LogEvent
)

/**
 * Hierarchy of all possible log events.
 */
sealed class LogEvent {
    /**
     * Store instance was created.
     */
    object StoreCreated : LogEvent()

    /**
     * Store instance was finished (cancelled).
     */
    object StoreFinished : LogEvent()

    /**
     * All events related to reducer coroutine.
     */
    sealed class ReducerEvent : LogEvent() {
        /**
         * Reducer coroutine starts.
         */
        object Start : ReducerEvent()

        /**
         * Dispatching new state to [Store] [StateReceiver]s.
         */
        data class DispatchState(val state: Any) : ReducerEvent()

        /**
         * Receiving new input action from [Store].
         */
        data class InputAction(
            val action: Any,
            val state: Any
        ) : ReducerEvent()

        /**
         * Exception in [Reducer] function.
         */
        data class Exception(val reason: Throwable) : ReducerEvent()

        /**
         * Dispatching [action] to all side effects.
         */
        data class DispatchToSideEffects(val action: Any) : ReducerEvent()
    }

    /**
     * All events related to side effects coroutines.
     */
    sealed class SideEffectEvent : LogEvent() {
        abstract val name: String

        /**
         * Started side effect coroutine.
         */
        data class Start(
            override val name: String
        ) : SideEffectEvent()

        /**
         * Received new input [action] from reducer coroutine.
         */
        data class InputAction(
            override val name: String,
            val action: Any
        ) : SideEffectEvent()

        /**
         * Dispatching new [action] to reducer.
         */
        data class DispatchingToReducer(
            override val name: String,
            val action: Any
        ) : SideEffectEvent()

        /**
         * Custom log event.
         *
         * May be used by [SideEffect] implementation to provide custom log events
         * with [events] payload.
         */
        class Custom(
            override val name: String,
            val event: Any
        ) : SideEffectEvent()
    }
}

@UseExperimental(ExperimentalCoroutinesApi::class, ObsoleteCoroutinesApi::class)
internal class Logger(
    private val storeName: String,
    scope: CoroutineScope,
    private val logSinks: List<SendChannel<LogEntry>>,
    toLogSinksDispatcher: CoroutineDispatcher
) : SideEffectLogger {
    private val inputLogEventsChannel = Channel<LogEntry>(40)
    private val loggingEnabled = logSinks.isNotEmpty()

    init {
        if (loggingEnabled) {
            scope.launch(context = toLogSinksDispatcher) {
                for (event in inputLogEventsChannel) {
                    logSinks.forEach { it.send(event) }
                }
            }
        }
    }

    internal fun logEvent(event: () -> LogEvent) {
        if (loggingEnabled) {
            inputLogEventsChannel.offer(LogEntry(
                storeName,
                System.currentTimeMillis(),
                event()
            ))
        }
    }

    internal fun logAfterCancel(event: () -> LogEvent) {
        if (loggingEnabled) {
            // scope is cancelled - using GlobalScope to deliver event
            GlobalScope.launch {
                val logEntry = LogEntry(
                    storeName,
                    System.currentTimeMillis(),
                    event()
                )
                logSinks.forEach {
                    it.send(logEntry)
                }
            }
        }
    }

    override fun logSideEffectEvent(event: () -> LogEvent.SideEffectEvent) {
        if (loggingEnabled) {
            inputLogEventsChannel.offer(
                LogEntry(
                    storeName,
                    System.currentTimeMillis(),
                    event()
                )
            )
        }
    }
}
