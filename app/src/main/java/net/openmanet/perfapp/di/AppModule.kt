package net.openmanet.perfapp.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.openmanet.perfapp.core.AppClock
import net.openmanet.perfapp.core.SystemAppClock

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    abstract fun bindAppClock(impl: SystemAppClock): AppClock
}
