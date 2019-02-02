package com.freeletics.coredux

import android.app.Application
import android.view.ViewGroup
import com.freeletics.coredux.di.ApplicationComponent
import com.freeletics.coredux.di.ApplicationModule
import com.freeletics.coredux.di.DaggerApplicationComponent
import kotlinx.coroutines.Dispatchers
import timber.log.Timber

open class SampleApplication : Application() {
    init {
        Timber.plant(Timber.DebugTree())
    }

    val applicationComponent: ApplicationComponent by lazy {
        DaggerApplicationComponent.builder().apply {
            componentBuilder(this)
        }.build()
    }

    protected open fun componentBuilder(builder: DaggerApplicationComponent.Builder): DaggerApplicationComponent.Builder =
        builder.applicationModule(
            ApplicationModule(
                baseUrl = "https://api.github.com",
                androidScheduler = Dispatchers.Main,
                viewBindingInstantiatorMap = mapOf<Class<*>,
                        ViewBindingInstantiator>(
                    PopularRepositoriesActivity::class.java to { rootView: ViewGroup ->
                        PopularRepositoriesViewBinding(
                            rootView
                        )
                    }
                )
            )
        )
}
