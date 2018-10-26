package com.freeletics.coredux

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import com.freeletics.coredux.businesslogic.pagination.Action
import com.freeletics.coredux.businesslogic.pagination.PaginationStateMachine
import com.freeletics.coredux.di.AndroidScheduler
import com.jakewharton.rxrelay2.PublishRelay
import com.jakewharton.rxrelay2.Relay
import io.reactivex.Scheduler
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.Consumer
import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.cancel
import kotlinx.coroutines.experimental.launch
import javax.inject.Inject
import kotlin.coroutines.experimental.CoroutineContext

class PopularRepositoriesViewModel @Inject constructor(
    paginationStateMachine: PaginationStateMachine,
    @AndroidScheduler androidScheduler : Scheduler
) : ViewModel(), CoroutineScope {
    override val coroutineContext: CoroutineContext get() = Dispatchers.Main

    private val inputRelay: Relay<Action> = PublishRelay.create()
    private val mutableState = MutableLiveData<PaginationStateMachine.State>()
    private val disposables = CompositeDisposable()

    val input: Consumer<Action> = inputRelay
    val state: LiveData<PaginationStateMachine.State> = mutableState

    init {
        disposables.add(inputRelay.subscribe { paginationStateMachine.input.offer(it) })

        launch {
            for (state in paginationStateMachine.state) {
                mutableState.value = state
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        coroutineContext.cancel()
        disposables.dispose()
    }
}
