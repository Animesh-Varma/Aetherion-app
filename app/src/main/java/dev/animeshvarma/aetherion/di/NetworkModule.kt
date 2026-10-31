package dev.animeshvarma.aetherion.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.animeshvarma.aetherion.crypto.KeystoreManager
import dev.animeshvarma.aetherion.network.InstagramApiClient
import dev.animeshvarma.aetherion.util.ConnectionInfo
import dev.animeshvarma.aetherion.util.DeviceFingerprint
import dev.animeshvarma.aetherion.util.DeviceProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideCoroutineScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun provideDeviceFingerprint(@ApplicationContext context: Context): DeviceFingerprint {
        return DeviceFingerprint(context)
    }

    @Provides
    @Singleton
    fun provideKeystoreManager(
        @ApplicationContext context: Context,
        deviceFingerprint: DeviceFingerprint
    ): KeystoreManager {
        return KeystoreManager(context, deviceFingerprint)
    }

    @Provides
    @Singleton
    fun provideDeviceProfile(@ApplicationContext context: Context): DeviceProfile {
        return DeviceProfile(context)
    }

    @Provides
    @Singleton
    fun provideConnectionInfo(@ApplicationContext context: Context): ConnectionInfo {
        return ConnectionInfo(context)
    }

    @Provides
    @Singleton
    fun provideApiClient(
        deviceFingerprint: DeviceFingerprint,
        deviceProfile: DeviceProfile,
        connectionInfo: ConnectionInfo,
        keystoreManager: KeystoreManager,
        scope: CoroutineScope
    ): InstagramApiClient {
        return InstagramApiClient(
            deviceFingerprint = deviceFingerprint,
            deviceProfile = deviceProfile,
            connectionInfo = connectionInfo,
            keystoreManager = keystoreManager,
            scope = scope
        )
    }
}