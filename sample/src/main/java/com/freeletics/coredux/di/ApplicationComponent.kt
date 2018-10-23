package com.freeletics.coredux.di

import com.freeletics.coredux.PopularRepositoriesActivity
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules =[ApplicationModule::class] )
interface ApplicationComponent {

    fun inject(into: PopularRepositoriesActivity)
}
