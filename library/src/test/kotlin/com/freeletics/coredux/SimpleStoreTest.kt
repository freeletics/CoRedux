package com.freeletics.coredux

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object SimpleStoreTest : Spek({
    describe("A redux store without any side effects") {
        val storeInput by memoized { Channel<Actions>(1) }
        val store by memoized {
            storeInput.reduxStore<State, Actions>(
                initialState = State.Initial
            ) { oldState, newAction ->
                when (newAction) {
                    Actions.LoadItems -> State.LoadingItems
                    else -> oldState
                }
            }
        }

        it("should emit initial state") {
            runBlocking {
                assertEquals(State.Initial, store.receive())
            }
        }

        context("On new action ${Actions.LoadItems::class.simpleName}") {
            beforeEach {
                runBlocking { storeInput.send(Actions.LoadItems) }
            }

            it("should emit ${State.LoadingItems::class.simpleName}") {
                runBlocking { assertEquals(State.LoadingItems, store.receive(2).last()) }
            }
        }
    }
})
