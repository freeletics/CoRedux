package com.freeletics.coredux.log.timber

import com.freeletics.coredux.log.common.LoggerLogSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import timber.log.Timber

/**
 * Provides [com.freeletics.coredux.LogSink] implementation, that uses [Timber] class to send log output.
 *
 * @param scope to receive incoming log messages, default is [GlobalScope]
 */
class TimberLogSink(
    scope: CoroutineScope = GlobalScope
) : LoggerLogSink(scope) {
    override fun debug(
        tag: String,
        message: String,
        throwable: Throwable?
    ) {
        Timber.tag(tag)
        Timber.d(throwable, message)
    }

    override fun info(
        tag: String,
        message: String,
        throwable: Throwable?
    ) {
        Timber.tag(tag)
        Timber.i(throwable, message)
    }

    override fun warning(
        tag: String,
        message: String,
        throwable: Throwable?
    ) {
        Timber.tag(tag)
        Timber.w(throwable, message)
    }
}
