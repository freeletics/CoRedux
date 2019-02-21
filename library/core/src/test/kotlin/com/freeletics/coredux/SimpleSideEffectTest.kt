package com.freeletics.coredux

import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.fail
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

internal typealias SE = SimpleSideEffect<String, Int>

internal object SimpleSideEffectTest : Spek({
    describe("A ${SimpleSideEffect::class.simpleName}") {
        val scope by memoized { CoroutineScope(Dispatchers.Default) }
        val sideEffect by memoized {
            SE("test") { state, action, _, handler ->
                val currentState = state()
                when {
                    action == 1 && currentState == "" -> handler {
                        delay(10)
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

        beforeEach {
            scope.launch {
                with (sideEffect) {
                    start(inputChannel, stateAccessor, outputChannel, logger)
                }
            }
        }

        context("when current state is not \"\"") {
            beforeEach { stateAccessor.setCurrentState("some-other-state") }

            context("and on new 1 action") {
                beforeEach { scope.launch { inputChannel.send(1) } }

                it("should not call handler") {
                    runBlocking {
                        try {
                            withTimeout(100) {
                                val item = outputChannel.receive()
                                fail("Received $item")
                            }
                        } catch (e: TimeoutCancellationException) {}
                    }
                }
            }
        }

        context("when current state is \"\"") {
            context("and on new 2 action") {
                beforeEach { scope.launch { inputChannel.send(2) } }

                it("should not call handler") {
                    runBlocking {
                        try {
                            withTimeout(100) {
                                val item = outputChannel.receive()
                                fail("Received $item")
                            }
                        } catch (e: TimeoutCancellationException) {}
                    }
                }
            }

            context("and on new 1 action") {
                beforeEach { scope.launch { inputChannel.send(1) } }

                it("should send 100 action to output channel") {
                    runBlocking {
                        withTimeout(100) {
                            val item = outputChannel.receive()
                            assertEquals(100, item)
                        }
                    }
                }

                context("and on second immediate 1 action") {
                    beforeEach { scope.launch { inputChannel.send(1) } }

                    it("should send 100 action to output channel") {
                        runBlocking {
                            withTimeout(100) {
                                val item = outputChannel.receive()
                                assertEquals(100, item)
                            }
                        }
                    }

                    it("should cancel previous call to handler method") {
                        runBlocking {
                            withTimeout(100) {
                                delay(30)
                                val items = mutableListOf<Int>()
                                (0..2).forEach { _ ->
                                    outputChannel.poll()?.let { items.add(it) }
                                }
                                assertEquals(1, items.size)
                            }
                        }
                    }
                }
            }
        }
    }
})
