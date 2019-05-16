package com.freeletics.coredux.dsl

import com.freeletics.coredux.Reducer
import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST")
class ReducerBuilder<S : Any, A : Any> {
    internal lateinit var initialState: S
    @PublishedApi
    internal val reducerConditionsBuilders = arrayListOf<Reducer<S, A>>()

    fun withInitialState(initialState: S) {
        this.initialState = initialState
    }

    inline fun <reified SC : S> onState(
        noinline additionalCondition: (SC) -> Boolean = { true }
    ) = StateTypeWithCondition(SC::class, additionalCondition)

    inline fun <reified SC : A> onAction(
        noinline additionalCondition: (SC) -> Boolean = { true }
    ) = ActionTypeWithCondition(SC::class, additionalCondition)

    infix fun <SC : S, AC : A> StateTypeWithCondition<SC>.and(
        action: ActionTypeWithCondition<AC>
    ) = StateAndActionWithConditions(
        stateType,
        action.actionType,
        additionalCondition,
        action.additionalCondition
    )

    operator fun <SC : S, AC : A> StateTypeWithCondition<SC>.plus(
        action: ActionTypeWithCondition<AC>
    ) = and(action)

    infix fun <SC : S> StateTypeWithCondition<SC>.produce(
        producer: (currentState: SC, newAction: A) -> S
    ) {
        reducerConditionsBuilders.add { currentState, newAction ->
            if (stateType.isInstance(currentState) &&
                additionalCondition(currentState as SC)) {
                producer(currentState, newAction)
            } else {
                currentState
            }
        }
    }

    infix fun <SC : S, AC : A> StateAndActionWithConditions<SC, AC>.produce(
        producer: (currentState: SC, newAction: AC) -> S
    ) {
        reducerConditionsBuilders.add { currentState, newAction ->
            if (stateType.isInstance(currentState) &&
                additionalStateCondition(currentState as SC) &&
                actionType.isInstance(newAction) &&
                additionalActionCondition(newAction as AC)) {
                producer(currentState, newAction)
            } else {
                currentState
            }
        }
    }

    infix fun <AC : A> ActionTypeWithCondition<AC>.produce(
        producer: (currentState: S, newAction: AC) -> S
    ) {
        reducerConditionsBuilders.add { currentState, newAction ->
            if (actionType.isInstance(newAction) &&
                additionalCondition(newAction as AC)) {
                producer(currentState, newAction)
            } else {
                currentState
            }
        }
    }

    internal fun build(): Reducer<S, A> {
        require(reducerConditionsBuilders.isNotEmpty()) {
            "No reducers was defined"
        }

        return CombinedReducer(reducerConditionsBuilders.asSequence())
    }

    class StateTypeWithCondition<S : Any>(
        val stateType: KClass<S>,
        val additionalCondition: (S) -> Boolean
    )

    class ActionTypeWithCondition<A : Any>(
        val actionType: KClass<A>,
        val additionalCondition: (A) -> Boolean
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
