package com.heywood8.telegramnews.di

import com.heywood8.telegramnews.data.local.LocalRepositoryImpl
import com.heywood8.telegramnews.domain.repository.LocalRepository
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
    abstract fun bindLocalRepository(impl: LocalRepositoryImpl): LocalRepository
}
