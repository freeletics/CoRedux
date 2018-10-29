class: center, middle

# Implementing Redux: from RxJava to Coroutines

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

# CoRedux
