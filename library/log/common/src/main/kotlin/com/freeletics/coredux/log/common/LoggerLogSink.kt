package com.freeletics.coredux.log.common

import com.freeletics.coredux.LogEntry
import com.freeletics.coredux.LogEvent
import com.freeletics.coredux.LogSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor

/**
 * Provides base [LogSink] implementation, that can be used to add specific logger library implementation.
 *
 * @param scope to receive incoming log messages
 */
abstract class LoggerLogSink(
    scope: CoroutineScope
) : LogSink {
    @UseExperimental(ObsoleteCoroutinesApi::class)
    override val sink: SendChannel<LogEntry> = scope.actor {
        val startTimes = mutableMapOf<String, Long>()
        for (logEntry in channel) {
            if (logEntry.event is LogEvent.StoreCreated) startTimes[logEntry.storeName] = logEntry.time
            val timeDiff = logEntry.time - (startTimes[logEntry.storeName]
                ?: throw IllegalStateException("No start event received for ${logEntry.storeName}"))

            log(logEntry.storeName, timeDiff, logEntry.event)

            if (logEntry.event is LogEvent.StoreFinished) startTimes.remove(logEntry.storeName)
        }
    }

    private fun log(
        storeName: String,
        timeDiff: Long,
        event: LogEvent
    ) = when (event) {
        is LogEvent.StoreCreated -> storeName.logWithTimeDiff(timeDiff, "Created", logger = ::info)
        is LogEvent.StoreFinished -> storeName.logWithTimeDiff(timeDiff, "Finished", logger = ::info)
        is LogEvent.ReducerEvent.Start -> storeName.logWithTimeDiff(timeDiff, "[Reducer] Started", logger = ::debug)
        is LogEvent.ReducerEvent.DispatchState -> storeName.logWithTimeDiff(
            timeDiff,
            "[Reducer] Dispatching state: ${event.state}",
            logger = ::debug
        )
        is LogEvent.ReducerEvent.InputAction -> storeName.logWithTimeDiff(
            timeDiff,
            "[Reducer] Input action: ${event.action}, current state: ${event.state}",
            logger = ::debug
        )
        is LogEvent.ReducerEvent.Exception -> storeName.logWithTimeDiff(
            timeDiff,
            "[Reducer] Exception: ${event.reason}",
            event.reason,
            ::warning
        )
        is LogEvent.ReducerEvent.DispatchToSideEffects -> storeName.logWithTimeDiff(
            timeDiff,
            "[Reducer] Dispatching to side effects: ${event.action}",
            logger = ::debug
        )
        is LogEvent.SideEffectEvent.Start -> storeName.logWithTimeDiff(
            timeDiff,
            "[(SE) ${event.name}] Started",
            logger = ::debug
        )
        is LogEvent.SideEffectEvent.InputAction -> storeName.logWithTimeDiff(
            timeDiff,
            "[(SE) ${event.name}] Input action: ${event.action}",
            logger = ::debug
        )
        is LogEvent.SideEffectEvent.DispatchingToReducer -> storeName.logWithTimeDiff(
            timeDiff,
            "[(SE) ${event.name}] Dispatching action to reducer: ${event.action}",
            logger = ::debug
        )
        is LogEvent.SideEffectEvent.Custom -> storeName.logWithTimeDiff(
            timeDiff,
            "[(SE) ${event.name}] $event",
            logger = ::debug
        )
    }

    private inline fun String.logWithTimeDiff(
        timeDiff: Long,
        log: String,
        throwable: Throwable? = null,
        logger: (tag: String, message: String, throwable: Throwable?) -> Any
    ) {
        logger(this.replace(' ', '_'), "+${timeDiff}ms: $log", throwable)
    }

    /**
     * Print debug level log message.
     *
     * @param tag message unique tag that is created from store name
     * @param message log message
     * @param throwable optional throwable
     */
    abstract fun debug(tag: String, message: String, throwable: Throwable?)

    /**
     * Print info level log message.
     *
     * @param tag message unique tag that is created from store name
     * @param message log message
     * @param throwable optional throwable
     */
    abstract fun info(tag: String, message: String, throwable: Throwable?)

    /**
     * Print warning level log message.
     *
     * @param tag message unique tag that is created from store name
     * @param message log message
     * @param throwable optional throwable
     */
    abstract fun warning(tag: String, message: String, throwable: Throwable?)
}
