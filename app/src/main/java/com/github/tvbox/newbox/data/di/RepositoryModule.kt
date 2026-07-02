package com.github.tvbox.newbox.data.di

import com.github.tvbox.newbox.data.repository.DefaultSubscriptionRepository
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
}
