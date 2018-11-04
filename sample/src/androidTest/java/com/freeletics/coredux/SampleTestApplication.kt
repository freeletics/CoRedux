package com.freeletics.coredux

import android.view.ViewGroup
import com.freeletics.coredux.di.DaggerApplicationComponent
import com.freeletics.di.TestApplicationModule
import kotlinx.coroutines.Dispatchers

class SampleTestApplication : SampleApplication() {

    override fun componentBuilder(builder: DaggerApplicationComponent.Builder) =
        builder.applicationModule(
            TestApplicationModule(
                baseUrl = "http://127.0.0.1:$MOCK_WEB_SERVER_PORT",
                androidScheduler = Dispatchers.Main,
                viewBindingInstantiatorMap = mapOf<Class<*>, ViewBindingInstantiator>(
                    PopularRepositoriesActivity::class.java to { rootView: ViewGroup ->
                        RecordingPopularRepositoriesViewBinding(
                            rootView
                        )
                    }
                )
            )
        )

}
