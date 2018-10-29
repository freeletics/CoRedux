class: center, middle

# Implementing Redux: from RxJava to Coroutines

.footnote[By _Frank Hermann and Yahor Berdnikau_: Android developers **@Freeletics**]

---

# Agenda

1. Introduction to Redux
2. RxRedux
3. CoRedux

---

# Introduction to Redux: Motivation

--

- Client side state includes __server responses__, __cached data__ and __locally created data__

--

- Managing changing state is difficult

--

- React to continuous change through new requirements

--

- __Mutation and asynchronicity__ is difficult for the human brain

--

.center[![Mind blown](https://media.giphy.com/media/xT0xeJpnrWC4XWblEk/giphy.gif)]

---

# Introduction to Redux: Advantages

Based on __CQRS(Command Query Responsibility Segregation)__ and __Event Sourcing__

- Single source of truth

- State is read-only

- Changes are made with pure functions

---

# RxRedux

.center[<img src="https://raw.githubusercontent.com/freeletics/RxRedux/master/docs/rxredux.png" width="630" height="424"/>]

---

## RxRedux Example: State

```kotlin
data class Todo(
    val id: Int,
    val text: String,
    val isCompleted: Boolean
)

data class State(
    val todos: List<Todo>
)
```

---

## RxRedux Example: Action

```Kotlin
sealed class Action {
  data class AddTodo(val text: String) : Action()
  data class RemoveTodo(val id: Int) : Action()
  data class EditTodo(val id: Int, val text: String) : Action()
  data class CompleteTodo(val id: Int) : Action()
}
```

---

## RxRedux Example: Reducer

```Kotlin
fun reducer(state: State, action: Action): State =
  when (action) {
    is Action.AddTodo -> {
      val id = (state.todos.map(Todo::id).max() ?: -1) + 1
      val newTodo = Todo(
          id = id,
          text = action.text,
          isCompleted = false
      )
      State(todos = state.todos + newTodo)
    }

    // all remaining actions...
  }
}
```

---

## RxRedux Example: Store

```Kotlin
val inputActions = PublishSubject.create<Action>()

val initialState = State(todos = emptyList())

inputActions
    .reduxStore(initialState, emptyList(), ::reducer)
    .subscribe(::render)

inputActions.onNext(Action.AddTodo("Todo 1"))
inputActions.onNext(Action.AddTodo("Todo 2"))
inputActions.onNext(Action.EditTodo(1, "This is Todo 2"))
inputActions.onNext(Action.RemoveTodo(1))
inputActions.onNext(Action.CompleteTodo(0))
```

```
1: State(todos=[])
2: State(todos=[Todo(id=0, text=Todo 1, isCompleted=false)])
3: State(todos=[Todo(id=0, text=Todo 1, isCompleted=false),
                Todo(id=1, text=Todo 2, isCompleted=false)])
4: State(todos=[Todo(id=0, text=Todo 1, isCompleted=false),
                Todo(id=1, text=This is Todo 2, isCompleted=false)])
5: State(todos=[Todo(id=0, text=Todo 1, isCompleted=false)])
6: State(todos=[Todo(id=0, text=Todo 1, isCompleted=true)])
```

---

class: center, middle

# CoRedux implementation

---

# Channels

Channels provide a way to transfer stream of values between coroutines and provide
_"producer-consumer"_ pattern solution.

???

Channels will still be experimental.

---

# Channels

Creating a `Channel` is easy:
``` kotlin
val tasks = Channel<Task>()

```
--
`Channel` implements two interfaces:
- `ReceiveChannel` - defines `public suspend fun receive(): E` method
- `SendChannel` - defines `suspend fun send(element: E)` method

---

# Channels

Client 1 and Client 2:

``` kotlin
val task = Task(..)
tasks.send(task)
```

--

Worker 1:

``` kotlin
while(true) {
  val task = tasks.receive()
  processTask(task)
}
```

???

Worker1 start - suspends in `receive()`
Client1 sends first task - Rendevous!.
While worker processing task1 - client2 comes and sends second task -
sleeps until worker will be ready to process second task. -> Rendezvous Channel

---

# Channels

Buffered channel:

``` kotlin
val channel = Channel<Task>(capacity = 1)
```

???

First item will be send without any suspending, if no workers
available.

---

# Channels

ConflatedChannel:

``` kotlin
val channel = Channel<Task>(capacity = -1)

```

???

 Channel that buffers at most one element and conflates all subsequent `send` and `offer` invocations,
 so that the receiver always gets the most recently sent element.

---

# Channels

BroadcastChannel:

``` kotlin
val channel = BroadcastChannel<Task>(capacity = 10)

GlobalScope.launch {
   val task = channel.openSubscription().receive()
   task.execute()
}
```

???

 * Broadcast channel with array buffer of a fixed [capacity].
 * Sender suspends only when buffer is full due to one of the receives being slow to consume and
 * receiver suspends only when buffer is empty.

---

# Channels

Useful bridges between channels and Rx streams in `kotlinx-coroutines-rx2`:
- `openSubscription()` - Subscribes to observable and returns `ReceiveChannel`
- `asObservable()` - converts streaming channel to **hot** observable

---

# Side effects

RxJava `SideEffect`:

``` kotlin
typealias SideEffect<S, A> = (
  actions: Observable<A>,
  state: StateAccessor<S>
) -> Observable<out A>
```
--

Coroutine `SideEffect`:

``` kotlin
typealias SideEffect<S, A> = suspend (
  actions: ReceiveChannel<A>,
  state: StateAccessor<S>
) -> ReceiveChannel<A>
```

---

# Side effects

``` kotlin
sideEffects.forEach { sideEffect ->
  val actionsChannel = actionsSubject.openSubscription()
  launch {
    val actionOutput = sideEffect(
      actionsChannel, storeObserver::currentState)
*   for(action in actionOutput) {
      actionsSubject.onNext(action)
    }
  }
}
```

---

# First iteration on redux store

``` kotlin
fun <S: Any, A: Any> ReceiveChannel<A>.reduxStore(
    initialState: S,
    sideEffects: List<SideEffect<S, A>>,
*   coroutineScope: CoroutineScope = GlobalScope,
    reducer: Reducer<S, A>
): ReceiveChannel<S> { .. }
```

---

# First iteration on redux store

``` kotlin
fun reduxStore(..) {
    val output = Channel<S>()
    val actionsChannel = BroadcastChannel<A>(1 + sideEffects.size)
    var currentState = initialState

    output.invokeOnClose {
        this@reduxStore.cancel()
        actionsChannel.close()
    }

    coroutineScope.launch { output.send(currentState) }

    ...
    return output
}
```

---

# First iteration on redux store

``` kotlin
fun reduxStore(..) { ...
  coroutineScope.launch {
    try {
      for (action in actionsChannel.openSubscription()) {
        try {
          currentState = reducer(currentState, action)
        } catch (e: Throwable) {
          output.close(ReducerException(currentState, action, e))
        }
        coroutineScope.launch { output.send(currentState) }
      }
    } catch (e: Exception) { output.close(e)
    } finally { output.close() }
  }
... }
```

---

# First iteration on redux store

``` kotlin
fun reduxStore(..) { ...
  sideEffects.forEach { sideEffect ->
    coroutineScope.launch {
      try {
        sideEffect(actionsChannel.openSubscription()) {
          return@sideEffect currentState
        }.toChannel(actionsChannel)
      } catch (e: Exception) {
        actionsChannel.close(e)
      }
    }
  ...
}
```

---

# First iteration on redux store

``` kotlin
fun reduxStore(..) {
  coroutineScope.launch {
    try {
      this@reduxStore.toChannel(actionsChannel)
    } catch (e: Exception) {
      actionsChannel.close(e)
    } finally {
      actionsChannel.close()
    }
  }
}
```

---

# Current state

It works... But still very Rx'ish...

---

# Possible future ways

- Replace input and output channels with `dispatchAction()` and `observeState()` methods

--

- Introduce two types of `SideEffect` - single output `Action` and `ReceiveChannel<Action>`

---

# Possible future ways

Provide redux store DSL:

``` kotlin
val store = reduxStore(initialState = Init) {
  action<ActionType> { .. }
  action<ActionType> { .. }

  reducer { state, action -> .. }
  observeState(::render)
}

store.dispatch(LoadAction)
```

---

class: center, middle

# Questions?
