package com.freeletics.coredux

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
internal object StoreWithSideEffectsTest : Spek({
    describe("A redux store with only ${::stateLengthSE.name} side effect") {
        val loadDelay = 100L
        val stateReceiver by memoized { TestStateReceiver<String>() }
        val testScope by memoized { TestCoroutineScope(Job()) }
        val store by memoized {
            testScope.createStore(
                name = "Store with one side effect",
                initialState = "",
                sideEffects = listOf(stateLengthSE(loadDelay = loadDelay, lengthLimit = 2))
            ) { currentState, newAction ->
                currentState + newAction
            }
        }

        beforeEach { store.subscribe(stateReceiver) }
        afterEach {
            testScope.cancel()
            testScope.cleanupTestCoroutines()
        }

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

            context("and if $loadDelay ms passed") {
                beforeEach { testScope.advanceTimeBy(loadDelay) }

                it("should emit \"11\" as a third state") {
                    stateReceiver.assertStates(
                        "",
                        "1",
                        "11"
                    )
                }
            }

            context("then immediately on 5 action and after $loadDelay ms passed") {
                beforeEach {
                    store.dispatch(5)
                    testScope.advanceTimeBy(loadDelay)
                }

                it("should emit only 4 states") {
                    assertEquals(4, stateReceiver.stateUpdates.size)
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
        val testScope by memoized { TestCoroutineScope(Job()) }
        val loggerSE by memoized { LoggerSE() }
        val stateLengthSE by memoized { stateLengthSE(lengthLimit = 4, loadDelay = 0L) }
        val multiplyActionSE by memoized { multiplyActionSE(updateDelay) }
        val logger by memoized { TestLogger(testScope) }
        val store by memoized {
            testScope.createStoreInternal(
                name = "Store with many side effects",
                initialState = "",
                logSinks = listOf(logger),
                logsDispatcher = Dispatchers.Unconfined,
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

        afterEach {
            testScope.cancel()
            testScope.cleanupTestCoroutines()
        }

        context("On 1 action") {
            beforeEach {
                store.dispatch(1)
            }

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
                assertTrue(stateReceiver.stateUpdates.size <= expectedStates.size)
            }

            it("${LoggerSE::class.simpleName} should receive all action in order") {
                assertEquals(
                    listOf(1, 1, 2, 3, 4),
                    loggerSE.receivedActions
                )
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
                    store.dispatch(100)
                    testScope.advanceTimeBy(updateDelay + 1)
                }

                val expectedStatesAfterDelay = listOf(
                    "",
                    "1",
                    "11",
                    "112",
                    "1123",
                    "11234",
                    "11234100",
                    "112341002000"
                )

                it("Should not emit more then ${expectedStatesAfterDelay.size + 1} states") {
                    assertTrue(stateReceiver.stateUpdates.size <= expectedStatesAfterDelay.size)
                }

                it("Should emit $expectedStatesAfterDelay states in order") {
                    stateReceiver.assertStates(*expectedStatesAfterDelay.toTypedArray())
                }

            }
        }
    }
})
