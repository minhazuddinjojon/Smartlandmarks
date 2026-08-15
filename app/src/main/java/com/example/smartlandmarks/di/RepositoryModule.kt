package com.example.smartlandmarks.di

import com.example.smartlandmarks.data.repository.LandmarkRepositoryImpl
import com.example.smartlandmarks.domain.repository.LandmarkRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    /**
     * The UI depends on the interface, never the implementation — which is what makes
     * ViewModels testable with a fake repository and no network at all.
     */
    @Binds
    @Singleton
    abstract fun bindLandmarkRepository(impl: LandmarkRepositoryImpl): LandmarkRepository
}
