package com.example.smartlandmarks.di

import android.content.Context
import androidx.room.Room
import com.example.smartlandmarks.data.local.AppDatabase
import com.example.smartlandmarks.data.local.dao.LandmarkDao
import com.example.smartlandmarks.data.local.dao.PendingCreateDao
import com.example.smartlandmarks.data.local.dao.VisitDao
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
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            // Version 1 has no migrations yet. Destructive fallback is deliberate for a
            // cache: worst case the app refetches, and no user-authored data is lost
            // because queued work is replayed from the same tables.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideLandmarkDao(database: AppDatabase): LandmarkDao = database.landmarkDao()

    @Provides
    fun provideVisitDao(database: AppDatabase): VisitDao = database.visitDao()

    @Provides
    fun providePendingCreateDao(database: AppDatabase): PendingCreateDao =
        database.pendingCreateDao()
}
