package com.github.tvbox.newbox.data.di

import android.content.Context
import com.github.tvbox.newbox.data.local.AppDatabase
import com.github.tvbox.newbox.data.local.dao.VodCollectDao
import com.github.tvbox.newbox.data.local.dao.VodRecordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        androidx.room.Room.databaseBuilder(
            context, AppDatabase::class.java, AppDatabase.DATABASE_NAME
        ).fallbackToDestructiveMigration().build()

    @Provides
    fun provideVodRecordDao(db: AppDatabase): VodRecordDao = db.vodRecordDao()

    @Provides
    fun provideVodCollectDao(db: AppDatabase): VodCollectDao = db.vodCollectDao()
}
