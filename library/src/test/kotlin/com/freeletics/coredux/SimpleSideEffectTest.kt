package com.freeletics.coredux

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

internal typealias SE = SimpleSideEffect<State, Actions>

internal object SimpleSideEffectTest : Spek({
    describe("A ${SimpleSideEffect::class.simpleName}") {
        val scope by memoized { CoroutineScope(Dispatchers.Default) }
        val sideEffect by memoized {
            SE { state, action, handler ->
                val currentState = state()
                when {
                    action == Actions.LoadItems && currentState == State.Initial -> handler {
                        delay(10)
                        Actions.ItemsLoaded(listOf("item"))
                    }
                    else -> null
                }
            }
        }
        val inputChannel by memoized { Channel<Actions>() }
        val outputChannel by memoized { Channel<Actions>(100) }
        val stateAccessor by memoized { TestStateAccessor(State.Initial) }

        beforeEach {
            scope.launch {
                with (sideEffect) {
                    start(inputChannel, stateAccessor, outputChannel)
                }
            }
        }

        context("when current state is not ${State.Initial::class.simpleName}") {
            beforeEach { stateAccessor.setCurrentState(State.LoadingItems) }

            context("and on new ${Actions.LoadItems::class.simpleName}") {
                beforeEach { scope.launch { inputChannel.send(Actions.LoadItems) } }

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

        context("when current state is ${State.Initial::class.simpleName}") {
            context("and on new ${Actions.ItemsLoaded::class.simpleName}") {
                beforeEach { scope.launch { inputChannel.send(Actions.ItemsLoaded(listOf("some"))) } }

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

            context("and on new ${Actions.LoadItems::class.simpleName}") {
                beforeEach { scope.launch { inputChannel.send(Actions.LoadItems) } }

                it("should send ${Actions.ItemsLoaded::class.simpleName} to output channel") {
                    runBlocking {
                        withTimeout(100) {
                            val item = outputChannel.receive()
                            assertEquals(Actions.ItemsLoaded(listOf("item")), item)
                        }
                    }
                }

                context("and on second immediate ${Actions.LoadItems::class.simpleName}") {
                    beforeEach { scope.launch { inputChannel.send(Actions.LoadItems) } }

                    it("should send ${Actions.ItemsLoaded::class.simpleName} to output channel") {
                        runBlocking {
                            withTimeout(100) {
                                val item = outputChannel.receive()
                                assertEquals(Actions.ItemsLoaded(listOf("item")), item)
                            }
                        }
                    }

                    it("should cancel previous call to handler method") {
                        runBlocking {
                            withTimeout(100) {
                                delay(30)
                                val items = mutableListOf<Actions>()
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
