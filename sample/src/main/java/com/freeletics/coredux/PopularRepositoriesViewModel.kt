package com.freeletics.coredux

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import com.freeletics.coredux.businesslogic.pagination.Action
import com.freeletics.coredux.businesslogic.pagination.PaginationStateMachine
import com.freeletics.coredux.di.AndroidScheduler
import com.jakewharton.rxrelay2.PublishRelay
import com.jakewharton.rxrelay2.Relay
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.Consumer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

class PopularRepositoriesViewModel @Inject constructor(
    paginationStateMachine: PaginationStateMachine,
    @AndroidScheduler private val androidScheduler : CoroutineDispatcher
) : ViewModel(), CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext get() = androidScheduler + job

    private val inputRelay: Relay<Action> = PublishRelay.create()
    private val mutableState = MutableLiveData<PaginationStateMachine.State>()
    private val disposables = CompositeDisposable()

    val input: Consumer<Action> = inputRelay
    val state: LiveData<PaginationStateMachine.State> = mutableState

    init {
        val paginationStore = paginationStateMachine.create(this)
        disposables.add(inputRelay.subscribe {
            paginationStore.dispatch(it)
        })

        paginationStore.subscribe({ newState: PaginationStateMachine.State ->
            mutableState.value = newState
        }.distinctUntilChangedBy { previousState, newState ->
            previousState != newState
        })
    }

    override fun onCleared() {
        super.onCleared()
        job.cancel()
        disposables.dispose()
    }
}
