package com.cartvlab.android

import android.content.Context
import com.cartvlab.automotive.*
import com.cartvlab.core.common.EvidenceJournal
import com.cartvlab.core.datastore.*
import com.cartvlab.diagnostics.*
import com.cartvlab.mirroring.*
import com.cartvlab.player.*
import com.cartvlab.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun store(@ApplicationContext context: Context): SettingsStore = AppStore(context)
    @Provides @Singleton fun journal(store: SettingsStore) = EvidenceJournal(store)
    @Provides fun player(@ApplicationContext context: Context, store: SettingsStore, journal: EvidenceJournal): PlayerRepository = Media3PlayerController(context, store, journal)
    @Provides @Singleton fun projection(@ApplicationContext context: Context): MediaProjectionController = AndroidMediaProjectionController(context)
    @Provides @Singleton fun displays(@ApplicationContext context: Context): VehicleDisplayManager = AndroidVehicleDisplayManager(context)
    @Provides @Singleton fun automotive(): AndroidAutoCapabilityRepository = PublicAndroidAutoCapabilityRepository()
    @Provides @Singleton fun connection(): CarConnectionDiagnostics = UnknownCarConnectionDiagnostics()
    @Provides @Singleton fun device(@ApplicationContext context: Context, displays: VehicleDisplayManager): DeviceInfoRepository = AndroidDeviceInfoRepository(context, displays)
    @Provides @Singleton fun permissions(@ApplicationContext context: Context): PermissionInspector = AndroidPermissionInspector(context)
    @Provides @Singleton fun exporter(@ApplicationContext context: Context, device: DeviceInfoRepository,
        permissions: PermissionInspector, automotive: AndroidAutoCapabilityRepository,
        connection: CarConnectionDiagnostics, journal: EvidenceJournal) = ReportExporter(context, device, permissions, automotive, connection, journal)
    @Provides @Singleton fun settings(store: SettingsStore) = SettingsRepository(store)
}
