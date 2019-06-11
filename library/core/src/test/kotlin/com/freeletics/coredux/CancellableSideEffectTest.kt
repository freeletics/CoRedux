package com.freeletics.coredux

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

private const val DEFAULT_DELAY_MS = 10L

@ExperimentalCoroutinesApi
object CancellableSideEffectTest : Spek({
    describe("A ${CancellableSideEffect::class.simpleName}") {
        val testScope by memoized { TestCoroutineScope() }
        val sideEffect by memoized {
            CancellableSideEffect<String, Int>("test") { state, action, _, handler ->
                val currentState = state()
                when {
                    action == 1 && currentState == "" -> handler { _, output ->
                        launch {
                            delay(DEFAULT_DELAY_MS)
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
            testScope.launch {
                with(sideEffect) {
                    start(inputChannel, stateAccessor, outputChannel, logger)
                }
            }
        }

        afterEach { testScope.cleanupTestCoroutines() }

        context("when current state is not \"\"") {
            beforeEach { stateAccessor.setCurrentState("some-other-state") }

            context("and on new 1 action") {
                beforeEach {
                    testScope.launch { inputChannel.send(1) }
                    testScope.advanceTimeBy(DEFAULT_DELAY_MS + 1)
                }

                it("should not call handler function") {
                    assertTrue(outputChannel.isEmpty)
                }
            }
        }

        context("when current state is \"\"") {
            context("and on new 2 action") {
                beforeEach {
                    testScope.launch { inputChannel.send(2) }
                    testScope.advanceTimeBy(DEFAULT_DELAY_MS + 1)
                }

                it("should not call handler function") {
                    assertTrue(outputChannel.isEmpty)
                }
            }
        }

        context("and on new 1 action") {
            beforeEach { testScope.launch { inputChannel.send(1) } }

            it("should send 2, 3, 4 actions to output channel") {
                val items = mutableListOf<Int>()
                repeat(3) {
                    testScope.advanceTimeBy(DEFAULT_DELAY_MS)
                    testScope.runBlockingTest { items.add(outputChannel.receive()) }
                }
                assertEquals(listOf(2, 3, 4), items.toList())
            }

            context("and on second immediate 1 action") {
                beforeEach { testScope.launch { inputChannel.send(1) } }

                it("should send 2, 3, 4 actions to output channel") {
                    val items = mutableListOf<Int>()
                    repeat(3) {
                        testScope.advanceTimeBy(DEFAULT_DELAY_MS)
                        testScope.runBlockingTest { items.add(outputChannel.receive()) }
                    }
                    assertEquals(listOf(2, 3, 4), items.toList())
                }

                it("should cancel previous call to handler function") {
                    val items = mutableListOf<Int>()
                    repeat(5) {
                        testScope.advanceTimeBy(DEFAULT_DELAY_MS)
                        outputChannel.poll()?.let { items.add(it) }
                    }
                    assertEquals(3, items.size)
                    assertTrue(outputChannel.isEmpty)
                }
            }
        }
    }
})
