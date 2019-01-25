package com.freeletics.coredux

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
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
            scope.reduxStore<String, Int>(
                initialState = "",
                stateReceiver = stateReceiver
            ) { _, newAction ->
                when {
                    newAction >= 0 -> newAction.toString()
                    else -> null
                }
            }
        }

        it("should emit initial state") {
            store.toString()
            stateReceiver.assertStates("")
        }

        context("On new action 1") {
            beforeEach {
                store(1)
            }

            it("should emit \"1\"") {
                assertEquals("1", stateReceiver.receivedStates(2).last())
            }
        }

        context("when scope is cancelled") {
            beforeEach {
                scope.cancel()
            }
            it("should not accept new actions") {
                assertFalse(scope.isActive)
                try {
                    store(2)
                    fail("No exception was thrown")
                } catch (e: IllegalStateException) {}
            }
        }

        context("on actions from 0 to 100 send immediately") {
            val actions = (0..100)
            beforeEach { actions.forEach { store(it) } }

            it("store should process them in send order") {
                stateReceiver.assertStates("", *actions.map { it.toString() }.toTypedArray())
            }
        }
    }
})
