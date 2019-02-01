package com.freeletics.coredux

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

/**
 * A [createStore] is a Kotlin coroutine based implementation of Redux and redux.js.org.
 *
 * @param initialState The initial state. This one will be emitted directly in onSubscribe()
 * @param sideEffects The sideEffects. See [SideEffect].
 * @param launchMode store launch mode. Default is [CoroutineStart.LAZY] - when first [StateReceiver]
 * will added to [Store], it will start processing input actions.
 * @param reducer The reducer.  See [Reducer].
 * @param S The type of the State
 * @param A The type of the Actions
 *
 * @return instance of [Store] object
 */
@UseExperimental(ExperimentalCoroutinesApi::class)
fun <S: Any, A: Any> CoroutineScope.createStore(
    initialState: S,
    sideEffects: List<SideEffect<S, A>> = emptyList(),
    launchMode: CoroutineStart = CoroutineStart.LAZY,
    reducer: Reducer<S, A>
): Store<S, A> {
    val actionsReducerChannel = Channel<A>(Channel.UNLIMITED)
    val actionsSideEffectsChannel = BroadcastChannel<A>(sideEffects.size + 1)

    coroutineContext[Job]?.invokeOnCompletion {
        actionsReducerChannel.close(it)
        actionsSideEffectsChannel.close(it)
    }

    return CoreduxStore(actionsReducerChannel) { stateDispatcher ->
        // Creating reducer coroutine
        launch(start = launchMode) {
            var currentState = initialState

            // Sending initial state
            stateDispatcher(currentState)

            // Starting side-effects coroutines
            sideEffects.forEach { sideEffect ->
                with (sideEffect) {
                    start(
                        actionsSideEffectsChannel.openSubscription(),
                        { currentState },
                        actionsReducerChannel
                    )
                }
            }

            try {
                for (action in actionsReducerChannel) {
                    currentState = try {
                        reducer(currentState, action)
                    } catch (e: Throwable) {
                        throw ReducerException(currentState, action, e)
                    }

                    stateDispatcher(currentState)

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
    private val reducerCoroutine = reducerCoroutineBuilder { newState ->
        stateReceiversList.forEach {
            it(newState)
        }
    }
    private var stateReceiversList = emptyList<StateReceiver<S>>()

    @UseExperimental(ExperimentalCoroutinesApi::class)
    override fun dispatch(action: A) {
        if (actionsDispatchChannel.isClosedForSend) throw IllegalStateException("CoroutineScope is cancelled")

        if (!actionsDispatchChannel.offer(action)) {
            throw IllegalStateException("Input actions overflow - buffer is full")
        }
    }


    override fun subscribe(subscriber: StateReceiver<S>) {
        val receiversIsEmpty = stateReceiversList.isEmpty()
        stateReceiversList = stateReceiversList + subscriber
        if (receiversIsEmpty &&
            !reducerCoroutine.isActive) {
            reducerCoroutine.start()
        }
    }

    override fun unsubscribe(subscriber: StateReceiver<S>) {
        stateReceiversList = stateReceiversList - subscriber
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
typealias Reducer<S, A> = (S, A) -> S

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
typealias StateReceiver<S> = (S) -> Unit

/**
 * Filters [StateReceiver] by only passing states that are distinct from their predecessor state using [isStateDistinct]
 * function.
 *
 * @param [isStateDistinct] receives `previousState` (can be null initially), `newState` and outputs `true` if new state
 * should be passed to [StateReceiver] or `false` if not.
 */
fun <S : Any> StateReceiver<S>.distinctUntilChangedBy(
    isStateDistinct: (previousState: S?, newState: S) -> Boolean
): StateReceiver<S> {
    var previousState: S? = null

    return {
        if (isStateDistinct(previousState, it)) {
            previousState = it
            this(it)
        }
    }
}

/**
 * Filters [StateReceiver] by only passing states that are distinct from their predecessor state using states equality,
 * only non-equal states will pass.
 */
fun <S : Any> StateReceiver<S>.distinctUntilChanged(): StateReceiver<S> =
    distinctUntilChangedBy { previousState, newState ->
        previousState != newState
    }
