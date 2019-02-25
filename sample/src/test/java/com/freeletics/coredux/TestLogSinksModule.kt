package com.freeletics.coredux

import dagger.Module
import dagger.Provides
import dagger.multibindings.ElementsIntoSet
import javax.inject.Singleton

@Module
object TestLogSinksModule {
    @Provides
    @ElementsIntoSet
    @Singleton
    @JvmStatic
    fun provideNoLogSinks(): MutableSet<LogSink> = emptySet<LogSink>().toMutableSet()
}