package com.mdaopay.app.di

import android.content.Context
import androidx.room.Room
import com.mdaopay.app.core.datastore.AppDatabase
import com.mdaopay.app.core.datastore.TxQueueDao
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "mdaopay.db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideTxQueueDao(database: AppDatabase): TxQueueDao {
        return database.txQueueDao()
    }
}
