package com.freeletics.coredux.di

import com.freeletics.coredux.ViewBindingFactory
import com.freeletics.coredux.ViewBindingInstantiatorMap
import com.freeletics.coredux.businesslogic.github.GithubApi
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.experimental.CoroutineCallAdapterFactory
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.experimental.CoroutineDispatcher
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Singleton

@Module
open class ApplicationModule(
    private val baseUrl: String,
    private val viewBindingInstantiatorMap: ViewBindingInstantiatorMap,
    private val androidScheduler: CoroutineDispatcher
    ) {

    @Provides
    @Singleton
    open fun provideOkHttp(): OkHttpClient =
        OkHttpClient.Builder().build()

    @Provides
    @Singleton
    open fun provideGithubApi(okHttp: OkHttpClient): GithubApi {
        val retrofit =
            Retrofit.Builder()
                .client(okHttp)
                .addConverterFactory(MoshiConverterFactory.create())
                .addCallAdapterFactory(CoroutineCallAdapterFactory())
                .baseUrl(baseUrl)
                .build()

        return retrofit.create(GithubApi::class.java)
    }

    @Provides
    @Singleton
    fun provideViewBindingFactory() = ViewBindingFactory(viewBindingInstantiatorMap)

    @Provides
    @Singleton
    @AndroidScheduler
    fun androidScheduler() = androidScheduler
}
