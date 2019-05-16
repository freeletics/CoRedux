package com.freeletics.coredux.dsl

import com.freeletics.coredux.Reducer
import kotlin.reflect.KClass

class ReducerBuilder<S : Any, A : Any> {
    internal lateinit var initialState: S
    @PublishedApi
    internal val reducerConditionsBuilders = arrayListOf<Reducer<S, A>>()

    fun withInitialState(initialState: S) {
        this.initialState = initialState
    }

    inline fun <reified T : S> onState(
        noinline additionalCondition: (T) -> Boolean = { true }
    ) = StateTypeWithCondition(T::class, additionalCondition)

    inline fun <reified T : A> onAction(
        noinline additionalCondition: (T) -> Boolean = { true }
    ) = ActionTypeWithCondition(T::class, additionalCondition)

    infix fun <T : S, Z : A> StateTypeWithCondition<T>.and(
        action: ActionTypeWithCondition<Z>
    ) = StateAndActionWithConditions(
        stateType,
        action.actionType,
        additionalCondition,
        action.additionalCondition
    )

    infix operator fun <T : S, Z : A> StateTypeWithCondition<T>.plus(
        action: ActionTypeWithCondition<Z>
    ) = and(action)

    infix fun <T : S> StateTypeWithCondition<T>.produce(
        producer: (state: T, action: A) -> S
    ) {
        val reducer: Reducer<S, A> = { currentState, newAction ->
            if (stateType.isInstance(currentState) &&
                additionalCondition(currentState as T)) {
                producer(currentState, newAction)
            } else {
                currentState
            }
        }
    }

    infix fun <T : S, Z : A> StateAndActionWithConditions<T, Z>.produce(
        producer: (state: T, action: Z) -> S
    ) {
        val reducer: Reducer<S, A> = { currentState, newAction ->
            if (stateType.isInstance(currentState) &&
                additionalStateCondition(currentState as T) &&
                actionType.isInstance(newAction) &&
                additionalActionCondition(newAction as Z)) {
                producer(currentState, newAction)
            } else {
                currentState
            }
        }
        reducerConditionsBuilders.add(reducer)
    }

    internal fun build(): Reducer<S, A> {
        require(reducerConditionsBuilders.isNotEmpty()) {
            "No reducers was defined"
        }

        return CombinedReducer(reducerConditionsBuilders.asSequence())
    }

    class StateTypeWithCondition<S : Any>(
        val stateType: KClass<S>,
        val additionalCondition: (S) -> Boolean = { true }
    )

    class ActionTypeWithCondition<A : Any>(
        val actionType: KClass<A>,
        val additionalCondition: (A) -> Boolean = { true }
    )

    class StateAndActionWithConditions<S : Any, A : Any>(
        val stateType: KClass<S>,
        val actionType: KClass<A>,
        val additionalStateCondition: (S) -> Boolean,
        val additionalActionCondition: (A) -> Boolean
    )
}

internal class CombinedReducer<S : Any, A : Any>(
    private val reducers: Sequence<Reducer<S, A>>
): Reducer<S, A> {
    override fun invoke(currentState: S, newAction: A): S {
        return reducers
            .map { it(currentState, newAction) }
            .firstOrNull { it != currentState } ?: currentState
    }
}
