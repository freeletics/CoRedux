package com.freeletics.coredux

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@UseExperimental(ExperimentalCoroutinesApi::class)
object SimpleStoreTest : Spek({
    describe("A redux store without any side effects") {
        val stateReceiver by memoized { TestStateReceiver<String>() }
        val scope by memoized { CoroutineScope(Dispatchers.Default) }
        val store by memoized {
            scope.createStore<String, Int>(
                initialState = ""
            ) { _, newAction ->
                when {
                    newAction >= 0 -> newAction.toString()
                    else -> null
                }
            }
        }

        context("that has been subscribed immediately") {
            beforeEach { store.subscribe(stateReceiver) }
            afterEach { store.unsubscribe(stateReceiver) }

            it("should emit initial state") {
                stateReceiver.assertStates("")
            }

            context("On new action 1") {
                beforeEach {
                    store.dispatch(1)
                }

                it("should emit \"1\"") {
                    assertEquals("1", stateReceiver.receivedStates(2).last())
                }
            }

            context("when scope is cancelled") {
                beforeEach {
                    scope.cancel()
                    // Cancel is wait scope child jobs to cancel itself, so adding small delay to remove test flakiness
                    runBlocking { delay(10) }
                }
                it("should not accept new actions") {
                    assertFalse(scope.isActive)
                    try {
                        store.dispatch(2)
                        fail("No exception was thrown")
                    } catch (e: IllegalStateException) {
                    }
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
                beforeEach { store.dispatch(1) }
                context("and when new subscriber subscribes") {
                    beforeEach { store.subscribe(stateReceiver) }

                    it("subscriber should receive initial and 1 state") {
                        stateReceiver.assertStates("", "1")
                    }

                    val secondStateReceiver by memoized { TestStateReceiver<String>() }

                    context("and when second subscriber subscribes after 1 ms") {
                        beforeEach {
                            runBlocking {
                                delay(1)
                                store.subscribe(secondStateReceiver)
                            }
                        }

                        it("second state receiver should receive no states") {
                            try {
                                val states = secondStateReceiver.receivedStates(1)
                                fail("Received states: $states")
                            } catch (e: TimeoutCancellationException) {}
                        }

                        context("On new action 2") {
                            beforeEach {
                                store.dispatch(2)
                            }

                            it("both subscribers should receive \"2\" state") {
                                assertEquals("2", stateReceiver.receivedStates(3).last())
                                assertEquals("2", secondStateReceiver.receivedStates(1).last())
                            }
                        }
                    }
                }
            }
        }
    }
})
