package com.github.tvbox.newbox.data.di

import com.github.tvbox.newbox.data.repository.CollectRepository
import com.github.tvbox.newbox.data.repository.DefaultCollectRepository
import com.github.tvbox.newbox.data.repository.DefaultHistoryRepository
import com.github.tvbox.newbox.data.repository.DefaultSubscriptionRepository
import com.github.tvbox.newbox.data.repository.HistoryRepository
import com.github.tvbox.newbox.data.repository.SubscriptionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSubscriptionRepository(
        impl: DefaultSubscriptionRepository,
    ): SubscriptionRepository

    @Binds
    @Singleton
    abstract fun bindCollectRepository(
        impl: DefaultCollectRepository,
    ): CollectRepository

    @Binds
    @Singleton
    abstract fun bindHistoryRepository(
        impl: DefaultHistoryRepository,
    ): HistoryRepository
}
