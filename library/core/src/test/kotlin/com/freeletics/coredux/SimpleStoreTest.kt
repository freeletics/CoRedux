package com.freeletics.coredux

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@ObsoleteCoroutinesApi
@UseExperimental(ExperimentalCoroutinesApi::class)
object SimpleStoreTest : Spek({
    describe("A redux store without any side effects") {
        val testScope by memoized { TestCoroutineScope(Job()) }
        val stateReceiver by memoized { TestStateReceiver<String>() }
        val testLogger by memoized { TestLogger(testScope) }
        val store by memoized {
            testScope.createStoreInternal<String, Int>(
                name = "SimpleStore",
                initialState = "",
                logSinks = listOf(testLogger),
                logsDispatcher = Dispatchers.Unconfined
            ) { currentState, newAction ->
                when {
                    newAction >= 0 -> newAction.toString()
                    else -> currentState
                }
            }
        }

        afterEach {
            testScope.cancel()
            testScope.cleanupTestCoroutines()
        }

        context("that has been subscribed immediately") {
            beforeEach {
                store.subscribe(stateReceiver)
                testScope.advanceUntilIdle()
            }

            afterEach {
                store.unsubscribe(stateReceiver)
            }

            it("should emit initial state") {
                stateReceiver.assertStates("")
            }

            it("should emit three log events") {
                testLogger.assertLogEvents(
                    LogEvent.StoreCreated,
                    LogEvent.ReducerEvent.Start,
                    LogEvent.ReducerEvent.DispatchState("")
                )
            }

            context("On new action 1") {
                beforeEach {
                    store.dispatch(1)
                }

                it("should first emit input action log event") {
                    assertEquals(
                        LogEvent.ReducerEvent.InputAction(1, ""),
                        testLogger.logEvents[3].event
                    )
                }

                it("should emit \"1\" state") {
                    assertEquals("1", stateReceiver.stateUpdates.last())
                }

                it("should last emit dispatch state log event") {
                    assertEquals(
                        LogEvent.ReducerEvent.DispatchState("1"),
                        testLogger.logEvents[4].event
                    )
                }
            }

            context("when scope is cancelled") {
                beforeEach {
                    testScope.cancel()
                    // Cancel is wait scope child jobs to cancel itself, so adding small delay to remove test flakiness
                    runBlocking { delay(10) }
                }

                it("should not accept new actions") {
                    assertFalse(testScope.isActive)
                    try {
                        store.dispatch(2)
                        fail("No exception was thrown")
                    } catch (e: IllegalStateException) {}
                }
            }

            context("on actions from 0 to 100 send immediately") {
                val actions = (0..50)
                beforeEach { actions.forEach { store.dispatch(it) } }

                it("store should process them in send order") {
                    stateReceiver.assertStates("", *actions.map { it.toString() }.toTypedArray())
                }
            }
        }

        context("that is not subscribed") {
            context("on new action 1") {
                beforeEach {
                    store.dispatch(1)
                }

                it("should emit store create as a first log event") {
                    testLogger.assertLogEvents(LogEvent.StoreCreated)
                }

                context("and when new subscriber subscribes") {
                    beforeEach { store.subscribe(stateReceiver) }

                    it("subscriber should receive initial and 1 state") {
                        stateReceiver.assertStates("", "1")
                    }

                    val secondStateReceiver by memoized { TestStateReceiver<String>() }

                    context("and when second subscriber subscribes after 1 ms") {
                        beforeEach {
                            testScope.runBlockingTest {
                                delay(10)
                                store.subscribe(secondStateReceiver)
                            }
                        }

                        it("second state receiver should receive no states") {
                            assertEquals(0, secondStateReceiver.stateUpdates.size)
                        }

                        context("On new action 2") {
                            beforeEach {
                                store.dispatch(2)
                            }

                            it("both subscribers should receive \"2\" state") {
                                assertEquals("2", stateReceiver.stateUpdates.last())
                                assertEquals("2", secondStateReceiver.stateUpdates.last())
                            }
                        }
                    }
                }
            }
        }
    }
})
