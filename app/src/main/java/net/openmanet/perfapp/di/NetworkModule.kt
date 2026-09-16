package net.openmanet.perfapp.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Only the cloud-export client lives here. The mesh RPC client is intentionally NOT a
 * singleton: it's built per-connection in rpc/OpenManetClientFactory from the phone's currently
 * active network, so it can be bound to that specific network via Network.socketFactory rather
 * than however the process-wide default network happens to be routed.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    @CloudHttpClient
    fun provideCloudHttpClient(): OkHttpClient = OkHttpClient.Builder().build()
}
