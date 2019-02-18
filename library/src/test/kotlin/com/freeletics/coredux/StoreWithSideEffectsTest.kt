package com.freeletics.coredux

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

internal object StoreWithSideEffectsTest : Spek({
    describe("A redux store with only ${::stateLengthSE.name} side effect") {
        val stateReceiver by memoized { TestStateReceiver<String>() }
        val scope by memoized { CoroutineScope(Dispatchers.Default) }
        val store by memoized {
            scope.createStore(
                name = "Store with one side effect",
                initialState = "",
                sideEffects = listOf(stateLengthSE(lengthLimit = 2))
            ) { currentState, newAction ->
                currentState + newAction
            }
        }

        beforeEach { store.subscribe(stateReceiver) }

        it("should emit initial state") {
            stateReceiver.assertStates("")
        }

        context("on 1 action") {
            beforeEach { store.dispatch(1) }

            it("reducer should react first with \"1\" state") {
                stateReceiver.assertStates(
                    "",
                    "1"
                )
            }

            it("should emit \"11\" as a third state") {
                stateReceiver.assertStates(
                    "",
                    "1",
                    "11"
                )
            }

            context("and immediately on 5 action") {
                beforeEach { store.dispatch(5) }

                it("should emit only 4 states") {
                    try {
                        val collectedState = stateReceiver.receivedStates(5)
                        fail("Collected 5 states: $collectedState")
                    } catch (e: TimeoutCancellationException) {}
                }

                it("should emit states in order") {
                    stateReceiver.assertStates(
                        "",
                        "1",
                        "15",
                        "152"
                    )
                }
            }
        }
    }

    describe("A redux store with many side effects") {
        val updateDelay = 100L
        val stateReceiver by memoized { TestStateReceiver<String>() }
        val scope by memoized { CoroutineScope(Dispatchers.Default) }
        val loggerSE by memoized { LoggerSE() }
        val stateLengthSE by memoized { stateLengthSE(lengthLimit = 4, loadDelay = 0L) }
        val multiplyActionSE by memoized { multiplyActionSE(updateDelay) }
        val logger by memoized { TestLogger() }
        val store by memoized {
            scope.createStore(
                name = "Store with many side effects",
                initialState = "",
                logSinks = listOf(logger),
                sideEffects = listOf(
                    stateLengthSE,
                    loggerSE,
                    multiplyActionSE
                )
            ) { currentState, newAction ->
                currentState + newAction
            }
        }

        beforeEach { store.subscribe(stateReceiver) }

        context("On 1 action") {
            beforeEach { store.dispatch(1) }

            val expectedStates = listOf(
                "",
                "1",
                "11",
                "112",
                "1123",
                "11234"
            )

            it("Should emit states $expectedStates in order") {
                stateReceiver.assertStates(*expectedStates.toTypedArray())
            }

            it("Should not receive more then ${expectedStates.size + 1} states") {
                try {
                    val collectedStates = stateReceiver.receivedStates(expectedStates.size + 1)
                    fail("Received more then ${expectedStates.size + 1} states: $collectedStates")
                } catch (e: TimeoutCancellationException) {}
            }

            it("${LoggerSE::class.simpleName} should receive all action in order") {
                runBlocking {
                    delay(1000)
                    assertEquals(
                        listOf(1, 1, 2, 3, 4),
                        loggerSE.receivedActions
                    )
                }
            }

            val expectedLogEvents = listOf(
                LogEvent.StoreCreated,
                LogEvent.ReducerEvent.Start,
                LogEvent.ReducerEvent.DispatchState(""),
                LogEvent.SideEffectEvent.Start(stateLengthSE.name),
                LogEvent.SideEffectEvent.Start(loggerSE.name),
                LogEvent.SideEffectEvent.Start(multiplyActionSE.name),
                LogEvent.ReducerEvent.InputAction(1, ""),
                LogEvent.ReducerEvent.DispatchState("1"),
                LogEvent.ReducerEvent.DispatchToSideEffects(1)
            )

            it("Should emit log events $expectedLogEvents") {
                logger.assertLogEvents(*expectedLogEvents.toTypedArray())
            }

            context("And after ${updateDelay + 1} delay on second 100 action") {
                beforeEach {
                    scope.launch {
                        delay(updateDelay + 1)
                        store.dispatch(100)
                    }
                }

                val expectedStates = listOf(
                    "",
                    "1",
                    "11",
                    "112",
                    "1123",
                    "11234",
                    "11234100",
                    "112341002000"
                )

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
