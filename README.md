# CoRedux

Opinionated [Redux](https://redux.js.org/) implementation using [Kotlin](https://kotlinlang.org/) [coroutines](https://github.com/Kotlin/kotlinx.coroutines) inspired by [RxRedux](https://github.com/freeletics/rxredux).

## Table of content
- [Getting started](#getting-started)
  - [Gradle](#gradle)
  - [Additional artifacts](#additional-artifacts)
- [What is CoRedux](#what-is-coredux)
  - [Basic concept](#basic-concept)
  - [Side effects](#side-effects)
  - [Implementation details](#implementation-details)
  - [Logging](#logging)
- [Sample app](#sample-app)

## Getting started

### Gradle

All release artifacts are hosted on [Maven Central](https://mvnrepository.com/repos/central):
```gradle
repositories {
    mavenCentral()
}

implementation "com.freeletics.coredux:core:1.1.0"
```

If you want to use latest snapshot release from `master` branch:
```gradle
repositories {
    // CoRedux snapshot repository
    maven {
        url "https://oss.sonatype.org/content/repositories/snapshots/"
    }
}

implementation "com.freeletics.coredux:core:1.2.0-SNAPSHOT"
```

### Additional artifacts

Following additional artifacts are also available:
- `com.freeletics.coredux:log-common:1.1.0` - provides abstract logger `LogSink` implementation
- `com.freeletics.coredux:log-android:1.1.0` - provides `LogSink` implementation, that utilizes `android.util.Log` class to print log messages
t- `com.freeletics.coredux:log-timber:1.1.0` - provides `LogSink` implementation, that utilizes [Timber](https://github.com/JakeWharton/timber) logger

## What is CoRedux

CoRedux is a predictable state container, that is using same approach as [Redux](https://redux.js.org/) with
ability to have additional side effects.

Implementation is based on [Kotlin](https://kotlinlang.org/) [coroutines](https://github.com/Kotlin/kotlinx.coroutines)
and inspired by both [RxRedux](https://github.com/freeletics/rxredux) library
and [Coroutines in practice by Roman Elizarov](https://www.youtube.com/watch?v=a3agLJQ6vt8) KotlinConf2018 talk.

### Basic concept

Imagine - we need to develop a calculator app that has on new instance of app start initial value (state) `0`.
Calculator only allows addition, deduction, multiplication and division operations.
Operations describe what should happen with the calculator current state and can be represented as following input actions:
```kotlin
sealed class CalculatorAction {
    data class Add(val value: Int) : CalculatorAction()
    data class Deduct(val value: Int) : CalculatorAction()
    data class Multiply(val value: Int) : CalculatorAction()
    data class Divide(val value: Int) : CalculatorAction()
}
```

With CoRedux you can create a store that will be a single source of current Calculator state
and will update it on each new incoming `CalculatorAction`:
```kotlin
val store = coroutineScope.createStore<Int, CalculatorAction>(
    name = "Calculator",
    initialState = 0,
    reducer = { currentState, newAction ->
        when (newAction) {
            is CalculatorAction.Add -> currentState + newAction.value
            is CalculatorAction.Deduct -> currentState - newAction.value
            is CalculatorAction.Multiply -> currentState * newAction.value
            is CalculatorAction.Divide -> currentState / newAction.value
        }
    }
)
```

Where `reducer` is a special function responsible for managing state. A `reducer` specify
how the state changes in response to actions sent to the store.
Remember that actions only describe what happened, but don't describe how the application's state changes.
That is the job of the `reducer`.

Finally you have to subscribe `StateReceiver` to the `store` instance to get the state updates over time:
```kotlin
store.subscribe { state -> updateUI(state) }
```

`StateReceiver` is a function that is called whenever the state of the store has been changed. You can think of it as a listener of store's state.
More than one `StateReceiver` can subscribe to the same `store` instance.

On each new UI _intention_, UI implementation just need to send (_dispatch_) it as an action to `store` instance:
```kotlin
store.dispatch(CalculatorAction.Add(10))
// Current state will become 10
store.dispatch(CalculatorAction.Deduct(1))
// Current state will become 9
store.dispatch(CalculatorAction.Add(1))
// Current state will become 10
store.dispatch(CalculatorAction.Divide(10))
// Current state will become 1
```

All actions will be processed in serialized order
and on each incoming action `reducer` function is called to compute current state.

`createStore` has two start modes:
- with `launchMode = CoroutineStart.LAZY` (default), `Store` will wait for first `StateReceiver` subscription,
emit `initialState` to this `StateReceiver` and start processing incoming actions.
- with `launchMode = CoroutineStart.DEFAULT`, `Store` will start processing incoming actions immediately. When any `StateReceiver` subscribe to such store instance,
it will receive first state update only on next incoming action.

### Side effects

Side effect is an interface with exactly one function - it receives incoming
actions, can get current store state at any time and may emit outgoing actions.
**So basically it is actions in and actions out.**

**Actions in** to side effects are emitted by store after calling `reducer` function.

**Actions out** are consumed back again by store and trigger calling `reducer` function.

Side effects are used to perform additional `Job` as a reaction on a certain action, for example,
making an HTTP request, I/O operations, writing to database and so on. Since they run in a `Job` they can run async.
Each `Job` is scoped to the context of the `store`.

Let's add an side effect that makes a network request each time when action is `CalculatorAction.Add`
and current state is `9`. If server responds with http code `200` - side effect should emit `CalculatorAction.Deduct(1)`.
```kotlin
val sideEffect = object : SideEffect<Int, CalculatorAction> {
    override val name: String = "network logger"

    override fun CoroutineScope.start(
        input: ReceiveChannel<CalculatorAction>,
        stateAccessor: StateAccessor<Int>,
        output: SendChannel<CalculatorAction>,
        logger: SideEffectLogger
    ): Job = launch(context = CoroutineName(name)) {
        for (inputAction in input) {
            logger.logSideEffectEvent { LogEvent.SideEffectEvent.InputAction(name, inputAction) }
            if (inputAction is CalculatorAction.Add &&
                stateAccessor() >= 0) {
                launch {
                    val response = makeNetworkCall()
                    logger.logSideEffectEvent {
                        LogEvent.SideEffectEvent.Custom(name, "Received network response: $response")
                    }
                    if (response == 200) {
                        val outputAction = CalculatorAction.Deduct(1)
                        logger.logSideEffectEvent { LogEvent.SideEffectEvent.DispatchingToReducer(name, outputAction) }
                        output.send(outputAction)
                    }
                }
            }
        }
    }
}
```

You must register your `sideEffect` inside `createStore(sideEffects = listOf(sideEffect))`:
```kotlin
val storeWithSideEffect = coroutineScope.createStore<Int, CalculatorAction>(
    name = "Calculator",
    initialState = 0,
    sideEffects = listOf(sideEffect),
    reducer = { currentState, newAction ->
        when (newAction) {
            is CalculatorAction.Add -> currentState + newAction.value
            is CalculatorAction.Deduct -> currentState - newAction.value
            is CalculatorAction.Multiply -> currentState * newAction.value
            is CalculatorAction.Divide -> currentState / newAction.value
        }
    }
)
```

When we subscribe to `storeWithSideEffect` and trigger side effect by dispatching right actions -
network request from side effect will be performed:
```kotlin
storeWithSideEffect.subscribe { state -> updateUI(state) }
storeWithSideEffect.dispatch(CalculatorAction.Deduct(1))
// Current state will become -1
storeWithSideEffect.dispatch(CalculatorAction.Add(11))
// Current state will become 10 and network request will happen
```

`CoRedux` also provides two simplified implementations of `SideEffect` that you might find useful:
- `SimpleSideEffect` that produces on one input action either one output action or no action:

```kotlin
val sideEffect = SimpleSideEffect<State, Action>("Update on server") {
        state, action, logger, handler ->
    when (action) {
        is Action.Update -> handler {
            val httpCode = sendToServer(action.value)
            if (httpCode == 200) Action.Updated else null
        }
        else -> null
    }
}
```

- `CancellableSideEffect` that cancels previously running `Job` and starts a new one if the same type of action is dispatched to the store:

```kotlin
val sideEffect = CancellableSideEffect<State, Action>("poll server") {
    state, action, logger, handler ->
    when (action) {
        StartPollingServer -> handler { name, output ->
            openServerPollConnection { update ->
                output.send(Action.PollUpdate(update))
            }
        }
        else -> null
    }
}
```

### Implementation details

Internally CoRedux starts main coroutine ("manager"), that is a single source of current state, 
which is defined in local scope of coroutine - this allows to prevent any concurrency problems on updating the state.
Furthermore "manager" coroutine itself is sequentially:
- starts all side effects coroutines ("workers"), that listens for incoming actions
- sets current state to initial state
- immediately pushes the initial state to the `StateReciever` (depends on `launchMode` parameter)
- starts listening for new actions emitted through `store.dispatch(action)` and from `SideEffect`s

On each new action "manager" coroutine is sequentially:
- triggers `reducer()` to update current state inside "manager" coroutine local scope
- sends updated current state to all `StateReceiver`s
- broadcast input action to all side effects "workers" coroutines

If a side effect emtis a new action this action is added at the end of the internal queue
of actions waiting to be dispatched to reducer and to other side effects.

### Logging

To log events you have to add a `LogSink` to your store.
Actually, you can add multiple `LogSink`s if you want to add multiple type of logging.
You do that by passing a list of `LogSink`s in `createStore( logSinks = listOf(logSink1, logSink2, ... )`.

Types of log events are limited and defined by `LogEvent` sealed class hierarchy.

`Store` instance will automatically send log events, while `SideEffect` implementations
should send log events by themselfs, using provided `logger`.

# Sample app

Сheck `sample/` directory for a sample android app that uses CoRedux to load and
display most popular java-language repositories and amount of starts they have.
