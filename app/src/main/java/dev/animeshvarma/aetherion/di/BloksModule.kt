package dev.animeshvarma.aetherion.di

import dev.animeshvarma.aetherion.bloks.BloksLoginOrchestrator
import dev.animeshvarma.aetherion.network.InstagramApiClient
import dev.animeshvarma.aetherion.util.DeviceFingerprint
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BloksModule {

    @Provides
    @Singleton
    fun provideBloksOrchestrator(
        apiClient: InstagramApiClient,
        deviceFingerprint: DeviceFingerprint
    ): BloksLoginOrchestrator {
        return BloksLoginOrchestrator(apiClient, deviceFingerprint)
    }
}