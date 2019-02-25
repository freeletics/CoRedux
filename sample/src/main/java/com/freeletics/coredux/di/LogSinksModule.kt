package com.freeletics.coredux.di

import com.freeletics.coredux.LogSink
import com.freeletics.coredux.log.android.AndroidLogSink
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import kotlinx.coroutines.GlobalScope
import javax.inject.Singleton

@Module
object LogSinksModule {
    @Provides
    @IntoSet
    @Singleton
    @JvmStatic
    fun androidStoreLogger(): LogSink = AndroidLogSink(GlobalScope)
}
