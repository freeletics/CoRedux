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
        val stateReceiver by memoized { TestStateReceiver<State>() }
        val scope by memoized { CoroutineScope(Dispatchers.Default) }
        val store by memoized {
            scope.reduxStore<State, Actions>(
                initialState = State.Initial,
                stateReceiver = stateReceiver
            ) { _, newAction ->
                when (newAction) {
                    Actions.LoadItems -> State.LoadingItems
                }
            }
        }

        it("should emit initial state") {
            store.dispatch(Actions.LoadItems)
            assertEquals(State.Initial, stateReceiver.receivedStates(1).first())
        }

        context("On new action ${Actions.LoadItems::class.simpleName}") {
            beforeEach {
                store.dispatch(Actions.LoadItems)
            }

            it("should emit ${State.LoadingItems::class.simpleName}") {
                assertEquals(State.LoadingItems, stateReceiver.receivedStates(2).last())
            }
        }

        context("when scope is cancelled") {
            beforeEach {
                store.dispatch(Actions.LoadItems)
                scope.cancel()
            }
            it("should not accept new actions") {
                assertFalse(scope.isActive)
                try {
                    store.dispatch(Actions.LoadItems)
                    fail("No exception was thrown")
                } catch (e: IllegalStateException) {}
            }
        }
    }
})
