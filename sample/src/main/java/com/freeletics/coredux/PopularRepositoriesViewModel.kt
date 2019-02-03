package com.freeletics.coredux

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import com.freeletics.coredux.businesslogic.pagination.Action
import com.freeletics.coredux.businesslogic.pagination.PaginationStateMachine
import com.freeletics.coredux.di.AndroidScheduler
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

    private val mutableState = MutableLiveData<PaginationStateMachine.State>()

    private val paginationStore = paginationStateMachine.create(this).also {
        it.subscribe({ newState: PaginationStateMachine.State ->
            mutableState.value = newState
        }.distinctUntilChanged())
    }

    val dispatchAction: (Action) -> Unit = paginationStore::dispatch
    val state: LiveData<PaginationStateMachine.State> = mutableState

    override fun onCleared() {
        super.onCleared()
        job.cancel()
    }
}
