package com.freeletics.coredux

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

object CancellableSideEffectTest : Spek({
    describe("A ${CancellableSideEffect::class.simpleName}") {
        val scope by memoized { CoroutineScope(Dispatchers.Default)}
        val sideEffect by memoized {
            CancellableSideEffect<String, Int>("test") { state, action, _, handler ->
                val currentState = state()
                when {
                    action == 1 && currentState == "" -> handler { _, output ->
                        launch {
                            delay(10)
                            (2..4).forEach { output.send(it) }
                        }
                    }
                    else -> null
                }
            }
        }
        val inputChannel by memoized { Channel<Int>() }
        val outputChannel by memoized { Channel<Int>() }
        val stateAccessor by memoized { TestStateAccessor("") }
        val logger by memoized { StubSideEffectLogger() }

        beforeEach {
            scope.launch {
                with(sideEffect) {
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
                            withTimeout(30) {
                                val item = outputChannel.receive()
                                fail("Received $item")
                            }
                        } catch (e: TimeoutCancellationException) {}
                    }
                }
            }
        }

        context("and on new 1 action") {
            beforeEach { scope.launch { inputChannel.send(1) } }

            it("should send 2, 3, 4 actions to output channel") {
                runBlocking {
                    withTimeout(30) {
                        val items = mutableListOf<Int>()
                        while (items.size < 3) {
                            items.add(outputChannel.receive())
                        }
                    }
                }
            }

            context("and on second immediate 1 action") {
                beforeEach { scope.launch { inputChannel.send(1) } }

                it("should send 2, 3, 4 actions to output channel") {
                    runBlocking {
                        withTimeout(30) {
                            val items = mutableListOf<Int>()
                            while (items.size < 3) {
                                items.add(outputChannel.receive())
                            }
                        }
                    }
                }

                it("should cancel previous call to handler function") {
                    runBlocking {
                        try {
                            withTimeout(100) {
                                val items = mutableListOf<Int>()
                                while (items.size < 10) {
                                    items.add(outputChannel.receive())
                                }
                                fail("Received more actions then expected: $items")
                            }
                        } catch (e: TimeoutCancellationException) {}
                    }
                }
            }
        }
    }
})
