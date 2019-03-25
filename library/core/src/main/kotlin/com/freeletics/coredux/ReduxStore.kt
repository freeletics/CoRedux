package com.freeletics.coredux

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * A [createStore] is a Kotlin coroutine based implementation of Redux and redux.js.org.
 *
 * @param name preferably unique name associated with this [Store] instance.
 * It will be used as a `name` param for [LogEntry] to distinguish log events from different store
 * instances.
 * @param initialState The initial state. This one will be emitted directly in onSubscribe()
 * @param sideEffects The sideEffects. See [SideEffect].
 * @param launchMode store launch mode. Default is [CoroutineStart.LAZY] - when first [StateReceiver]
 * will added to [Store], it will start processing input actions.
 * @param logSinks list of [LogSink] implementations, that will receive log events.
 * To disable logging, use [emptyList] (default).
 * @param reducer The reducer.  See [Reducer].
 * @param S The type of the State
 * @param A The type of the Actions
 *
 * @return instance of [Store] object
 */
@UseExperimental(ExperimentalCoroutinesApi::class)
fun <S: Any, A: Any> CoroutineScope.createStore(
    name: String,
    initialState: S,
    sideEffects: List<SideEffect<S, A>> = emptyList(),
    launchMode: CoroutineStart = CoroutineStart.LAZY,
    logSinks: List<LogSink> = emptyList(),
    reducer: Reducer<S, A>
): Store<S, A> {
    val logger = Logger(name, this, logSinks.map { it.sink })
    val actionsReducerChannel = Channel<A>(Channel.UNLIMITED)
    val actionsSideEffectsChannel = BroadcastChannel<A>(sideEffects.size + 1)

    coroutineContext[Job]?.invokeOnCompletion {
        logger.logAfterCancel { LogEvent.StoreFinished }
        actionsReducerChannel.close(it)
        actionsSideEffectsChannel.close(it)
    }

    logger.logEvent { LogEvent.StoreCreated }
    return CoreduxStore(actionsReducerChannel) { stateDispatcher ->
        // Creating reducer coroutine
        launch(
            start = launchMode,
            context = CoroutineName("$name reducer")
        ) {
            logger.logEvent { LogEvent.ReducerEvent.Start }
            var currentState = initialState

            // Sending initial state
            logger.logEvent { LogEvent.ReducerEvent.DispatchState(currentState) }
            stateDispatcher(currentState)

            // Starting side-effects coroutines
            sideEffects.forEach { sideEffect ->
                logger.logEvent { LogEvent.SideEffectEvent.Start(sideEffect.name) }
                with (sideEffect) {
                    start(
                        actionsSideEffectsChannel.openSubscription(),
                        { currentState },
                        actionsReducerChannel,
                        logger
                    )
                }
            }

            try {
                for (action in actionsReducerChannel) {
                    logger.logEvent { LogEvent.ReducerEvent.InputAction(action, currentState) }
                    currentState = try {
                        reducer(currentState, action)
                    } catch (e: Throwable) {
                        logger.logEvent { LogEvent.ReducerEvent.Exception(e) }
                        throw ReducerException(currentState, action, e)
                    }
                    logger.logEvent { LogEvent.ReducerEvent.DispatchState(currentState) }
                    stateDispatcher(currentState)

                    logger.logEvent { LogEvent.ReducerEvent.DispatchToSideEffects(action) }
                    actionsSideEffectsChannel.send(action)
                }
            } finally {
                if (isActive) cancel()
            }
        }
    }
}

/**
 * Provides methods to interact with [createStore] instance.
 */
interface Store<S : Any, A : Any> {
    /**
     * Dispatches new actions to given [createStore] instance.
     *
     * It is safe to call this method from different threads,
     * action will consumed on [createStore] [CoroutineScope] context.
     *
     * If `launchMode` for [createStore] is [CoroutineStart.LAZY] dispatched actions will be collected and passed
     * to reducer on first [subscribe] call.
     */
    fun dispatch(action: A)

    /**
     * Add new [StateReceiver] to the store.
     *
     * It is ok to call this method multiple times - each call will add a new [StateReceiver].
     */
    fun subscribe(subscriber: StateReceiver<S>)

    /**
     * Remove previously added via [subscriber] [StateReceiver].
     */
    fun unsubscribe(subscriber: StateReceiver<S>)
}

private class CoreduxStore<S: Any, A: Any>(
    private val actionsDispatchChannel: SendChannel<A>,
    reducerCoroutineBuilder: ((S) -> Unit) -> Job
) : Store<S, A> {
    private var stateReceiversList = emptyList<StateReceiver<S>>()

    private val lock = ReentrantReadWriteLock()

    private val reducerCoroutine = reducerCoroutineBuilder { newState ->
        lock.read {
            stateReceiversList.forEach {
                it(newState)
            }
        }
    }.also {
        it.invokeOnCompletion {
            lock.write {
                stateReceiversList = emptyList()
            }
        }
    }

    @UseExperimental(ExperimentalCoroutinesApi::class)
    override fun dispatch(action: A) {
        if (actionsDispatchChannel.isClosedForSend) throw IllegalStateException("CoroutineScope is cancelled")

        if (!actionsDispatchChannel.offer(action)) {
            throw IllegalStateException("Input actions overflow - buffer is full")
        }
    }


    override fun subscribe(subscriber: StateReceiver<S>) {
        if (reducerCoroutine.isCompleted) throw IllegalStateException("CoroutineScope is cancelled")

        lock.write {
            val receiversIsEmpty = stateReceiversList.isEmpty()
            stateReceiversList += subscriber
            if (receiversIsEmpty &&
                !reducerCoroutine.isActive) {
                reducerCoroutine.start()
            }
        }
    }

    override fun unsubscribe(subscriber: StateReceiver<S>) {
        lock.write {
            stateReceiversList -= subscriber
        }
    }
}

/**
 * A simple type alias for a reducer function.
 * A Reducer takes a State and an Action as input and produces a state as output.
 *
 * **Note**: Implementations of [Reducer] must be fast and _lock-free_.
 *
 * @param S The type of the state
 * @param A The type of the Actions
 */
typealias Reducer<S, A> = (currentState: S, newAction: A) -> S

/**
 * Wraps [Reducer] call exception.
 */
class ReducerException(
    state: Any,
    action: Any,
    cause: Throwable
) : RuntimeException("Exception was thrown by reducer, state = '$state', action = '$action'", cause)

/**
 * Type alias for a updated state receiver function.
 *
 * State update will always be received on [createStore] [CoroutineScope] thread.
 */
typealias StateReceiver<S> = (newState: S) -> Unit
