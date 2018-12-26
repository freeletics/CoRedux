package com.freeletics.coredux

import kotlinx.coroutines.GlobalScope
import org.junit.Assert.assertEquals
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object SimpleStoreTest : Spek({
    describe("A redux store without any side effects") {
        val stateReceiver by memoized { TestStateReceiver<State>() }
        val store by memoized {
            GlobalScope.reduxStore<State, Actions>(
                initialState = State.Initial,
                stateReceiver = stateReceiver,
                reducer = { oldState, newAction ->
                    when (newAction) {
                        Actions.LoadItems -> State.LoadingItems
                        else -> oldState
                    }
                }
            )
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
    }
})
