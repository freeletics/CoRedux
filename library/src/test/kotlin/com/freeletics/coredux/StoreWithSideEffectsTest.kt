package com.freeletics.coredux

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.Assert.fail
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

internal object StoreWithSideEffectsTest : Spek({
    describe("A redux store with only load items side effect") {
        val expectedItems = listOf("one", "two")
        val stateReceiver by memoized { TestStateReceiver<State>() }
        val scope by memoized { CoroutineScope(Dispatchers.Default) }
        val store by memoized {
            scope.reduxStore(
                initialState = State.Initial,
                stateReceiver = stateReceiver,
                sideEffects = listOf(loadItemsSE(expectedItems))
            ) { currentState, newAction ->
                when (newAction) {
                    Actions.LoadItems -> State.LoadingItems
                    is Actions.ItemsLoaded -> State.ShowItems(newAction.item)
                    else -> currentState
                }
            }
        }

        it("should emit initial state") {
            store.toString()
            stateReceiver.assertStates(State.Initial)
        }

        context("onn ${Actions.LoadItems::class.simpleName} action") {
            beforeEach { store.dispatch(Actions.LoadItems) }

            it("reducer should react first with ${State.LoadingItems::class.simpleName} state") {
                stateReceiver.assertStates(
                    State.Initial,
                    State.LoadingItems
                )
            }

            it("should emit show items state as a last state") {
                stateReceiver.assertStates(
                    State.Initial,
                    State.LoadingItems,
                    State.ShowItems(expectedItems)
                )
            }

            context("and immediately on second load item action") {
                beforeEach { store.dispatch(Actions.LoadItems) }

                it("should emit only 4 states") {
                    try {
                        val collectedState = stateReceiver.receivedStates(5)
                        fail("Collected 5 states: $collectedState")
                    } catch (e: TimeoutCancellationException) {}
                }

                it("should emit states in order") {
                    stateReceiver.assertStates(
                        State.Initial,
                        State.LoadingItems,
                        State.LoadingItems,
                        State.ShowItems(expectedItems)
                    )
                }
            }
        }
    }

    describe("A redux store with different side effects") {
        val expectedItems = listOf("one", "two")
        val updateDelay = 100L
        val stateReceiver by memoized { TestStateReceiver<State>() }
        val scope by memoized { CoroutineScope(Dispatchers.Default) }
        val store by memoized {
            scope.reduxStore(
                initialState = State.Initial,
                stateReceiver = stateReceiver,
                sideEffects = listOf(
                    loadItemsSE(expectedItems, 0L),
                    emptySE,
                    updateItemsSE(updateDelay)
                )
            ) { currentState, newAction ->
                when (newAction) {
                    Actions.LoadItems -> State.LoadingItems
                    is Actions.ItemsLoaded -> State.ShowItems(newAction.item)
                    is Actions.UpdateItem -> State.UpdateItem(newAction.updatedItem)
                }
            }
        }

        context("On ${Actions.LoadItems::class.simpleName} action") {
            beforeEach { store.dispatch(Actions.LoadItems) }

            val expectedStates = listOf(
                State.Initial,
                State.LoadingItems,
                State.ShowItems(expectedItems)
            ).run {
                this + expectedItems.map { item ->
                    State.UpdateItem(item + "_updated")
                }
            }

            it("Should emit states $expectedStates in order") {
                stateReceiver.assertStates(*expectedStates.toTypedArray())
            }

            it("Should not receive more then ${expectedStates.size + 1} states") {
                try {
                    val collectedStates = stateReceiver.receivedStates(expectedStates.size + 1)
                    fail("Received more then ${expectedStates.size + 1} states: $collectedStates")
                } catch (e: TimeoutCancellationException) {}
            }

            context("And after ${updateDelay + 1} delay on second ${Actions.LoadItems::class.simpleName} action") {
                beforeEach {
                    scope.launch {
                        delay(updateDelay + 1)
                        store.dispatch(Actions.LoadItems)
                    }
                }

                val expectedStates = listOf(
                    State.Initial,
                    State.LoadingItems,
                    State.ShowItems(expectedItems),
                    State.UpdateItem(expectedItems[0] + "_updated"),
                    State.LoadingItems,
                    State.ShowItems(expectedItems)
                ).run {
                    this + expectedItems.map { item ->
                        State.UpdateItem(item + "_updated")
                    }
                }

                it("Should emit $expectedStates states in order") {
                    stateReceiver.assertStates(*expectedStates.toTypedArray())
                }

                it("Should not emit more then ${expectedStates.size + 1} states") {
                    try {
                        val collectedStates = stateReceiver.receivedStates(expectedStates.size + 1)
                        fail("Received more then ${expectedStates.size + 1} states: $collectedStates")
                    } catch (e: TimeoutCancellationException) {}
                }
            }
        }
    }
})
