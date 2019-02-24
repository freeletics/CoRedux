package com.freeletics.coredux.log.android

import android.util.Log
import com.freeletics.coredux.LogEntry
import com.freeletics.coredux.LogEvent
import com.freeletics.coredux.LogSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor

/**
 * Provides [LogSink] implementation, that uses Android [Log] class to send log output.
 *
 * @param scope to receive incoming log messages.
 */
class AndroidLogSink(
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
        is LogEvent.StoreCreated -> storeName.logWithTimeDiff(timeDiff, "Created", Log::i)
        is LogEvent.StoreFinished -> storeName.logWithTimeDiff(timeDiff, "Finished", Log::i)
        is LogEvent.ReducerEvent.Start -> storeName.logWithTimeDiff(timeDiff, "[Reducer] Started", Log::d)
        is LogEvent.ReducerEvent.DispatchState -> storeName.logWithTimeDiff(
            timeDiff,
            "[Reducer] Dispatching state: ${event.state}",
            Log::d
        )
        is LogEvent.ReducerEvent.InputAction -> storeName.logWithTimeDiff(
            timeDiff,
            "[Reducer] Input action: ${event.action}, current state: ${event.state}",
            Log::d
        )
        is LogEvent.ReducerEvent.Exception -> storeName.logWithTimeDiff(
            timeDiff,
            "[Reducer] Exception: ${event.reason}",
            Log::w
        )
        is LogEvent.ReducerEvent.DispatchToSideEffects -> storeName.logWithTimeDiff(
            timeDiff,
            "[Reducer] Dispatching to side effects: ${event.action}",
            Log::d
        )
        is LogEvent.SideEffectEvent.Start -> storeName.logWithTimeDiff(
            timeDiff,
            "[(SE) ${event.name}] Started",
            Log::d
        )
        is LogEvent.SideEffectEvent.InputAction -> storeName.logWithTimeDiff(
            timeDiff,
            "[(SE) ${event.name}] Input action: ${event.action}",
            Log::d
        )
        is LogEvent.SideEffectEvent.DispatchingToReducer -> storeName.logWithTimeDiff(
            timeDiff,
            "[(SE) ${event.name}] Dispatching action to reducer: ${event.action}",
            Log::d
        )
        is LogEvent.SideEffectEvent.Custom -> storeName.logWithTimeDiff(
            timeDiff,
            "[(SE) ${event.name}] $event",
            Log::d
        )
    }

    private inline fun String.logWithTimeDiff(
        timeDiff: Long,
        log: String,
        logger: (tag: String, message: String) -> Any
    ) {
        logger(this.replace(' ', '_'), "+${timeDiff}ms: $log")
    }
}