package com.freeletics.coredux

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.filter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals

internal sealed class State {
    object Initial : State()
    object LoadingItems : State()
    data class ShowItems(val items: List<String>) : State()
    data class UpdateItem(val item: String) : State()
}

internal sealed class Actions {
    object LoadItems : Actions()
    data class ItemsLoaded(val item: List<String>) : Actions()
    data class UpdateItem(val updatedItem: String) : Actions()
}

internal typealias TestSE = SideEffect<State, Actions>

internal fun loadItemsSE(
    loadedItems: List<String>,
    loadDelay: Long = 100L
) = SimpleSideEffect<State, Actions> { action, _, handler ->
    when (action) {
        Actions.LoadItems -> handler {
            delay(loadDelay)
            Actions.ItemsLoaded(loadedItems)
        }
        else -> null
    }
}

internal val emptySE: TestSE = object : TestSE {
    override fun CoroutineScope.start(
        input: ReceiveChannel<Actions>,
        stateAccessor: StateAccessor<State>,
        output: SendChannel<Actions>
    ) = launch {
        // do nothing
    }
}

@UseExperimental(ObsoleteCoroutinesApi::class)
internal fun updateItemsSE(
    produceDelay: Long = 100L
) : TestSE = object : TestSE {
    override fun CoroutineScope.start(
        input: ReceiveChannel<Actions>,
        stateAccessor: StateAccessor<State>,
        output: SendChannel<Actions>
    ): Job = launch {
        var job: Job? = null
        for (action in input.filter { it is Actions.ItemsLoaded }) {
            job?.cancel()
            job = launch {
                (action as Actions.ItemsLoaded).item.forEach {
                    delay(produceDelay)
                    output.send(Actions.UpdateItem(it + "_updated"))
                }
            }
        }
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

internal class TestStateAccessor(
    private var currentState: State
) : StateAccessor<State> {
    fun setCurrentState(state: State) { currentState = state }

    override fun invoke(): State = currentState
}
