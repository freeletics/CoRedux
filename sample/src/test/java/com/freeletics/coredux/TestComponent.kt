package com.freeletics.coredux

import com.freeletics.coredux.businesslogic.github.GithubApi
import com.freeletics.coredux.businesslogic.pagination.PaginationStateMachine
import com.freeletics.coredux.di.ApplicationModule
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules =[
    ApplicationModule::class,
    TestLogSinksModule::class
] )
interface TestComponent {

    fun paginationStateMachine(): PaginationStateMachine

    fun airplaceModeDecoratedGithubApi() : GithubApi
}
