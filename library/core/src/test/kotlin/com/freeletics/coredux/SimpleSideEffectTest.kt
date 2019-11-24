package com.freeletics.coredux

import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertTrue
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

internal typealias SE = SimpleSideEffect<String, Int>
private const val DEFAULT_DELAY = 10L

@ExperimentalCoroutinesApi
internal object SimpleSideEffectTest : Spek({
    describe("A ${SimpleSideEffect::class.simpleName}") {
        val testScope by memoized { TestCoroutineScope() }
        val sideEffect by memoized {
            SE("test") { state, action, _, handler ->
                val currentState = state()
                when {
                    action == 1 && currentState == "" -> handler {
                        delay(DEFAULT_DELAY)
                        100
                    }
                    else -> null
                }
            }
        }
        val inputChannel by memoized { Channel<Int>() }
        val outputChannel by memoized { Channel<Int>(100) }
        val stateAccessor by memoized { TestStateAccessor("") }
        val logger by memoized { StubSideEffectLogger() }

        beforeEachTest {
            testScope.launch {
                with (sideEffect) {
                    start(inputChannel, stateAccessor, outputChannel, logger)
                }
            }
        }

        afterEachTest { testScope.cleanupTestCoroutines() }

        context("when current state is not \"\"") {
            beforeEachTest { stateAccessor.setCurrentState("some-other-state") }

            context("and on new 1 action") {
                beforeEachTest {
                    testScope.launch { inputChannel.send(1) }
                    testScope.advanceTimeBy(DEFAULT_DELAY + 1)
                }

                it("should not call handler") {
                    assertTrue(outputChannel.isEmpty)
                }
            }
        }

        context("when current state is \"\"") {
            context("and on new 2 action") {
                beforeEachTest {
                    testScope.launch { inputChannel.send(2) }
                    testScope.advanceTimeBy(DEFAULT_DELAY + 1)
                }

                it("should not call handler") {
                    assertTrue(outputChannel.isEmpty)
                }
            }

            context("and on new 1 action") {
                beforeEachTest {
                    testScope.launch { inputChannel.send(1) }
                }

                it("should send 100 action to output channel") {
                    testScope.advanceTimeBy(DEFAULT_DELAY)
                    testScope.runBlockingTest {
                        assertEquals(100, outputChannel.receive())
                    }
                }

                context("and on second immediate 1 action") {
                    beforeEachTest {
                        testScope.advanceTimeBy(DEFAULT_DELAY / 10)
                        testScope.launch { inputChannel.send(1) }
                        testScope.advanceTimeBy(DEFAULT_DELAY)
                    }

                    it("should send 100 action to output channel") {
                        testScope.runBlockingTest {
                            assertEquals(100, outputChannel.receive())
                        }
                    }

                    it("should cancel previous call to handler method") {
                        val items = mutableListOf<Int>()
                        repeat(3) {
                            outputChannel.poll()?.let { items.add(it) }
                            testScope.advanceTimeBy(DEFAULT_DELAY)
                        }
                        assertEquals(1, items.size)
                    }
                }
            }
        }
    }
})
