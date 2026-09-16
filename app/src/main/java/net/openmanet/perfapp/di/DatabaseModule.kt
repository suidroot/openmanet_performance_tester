package net.openmanet.perfapp.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.openmanet.perfapp.data.ManetDatabase
import net.openmanet.perfapp.data.dao.GpsFixDao
import net.openmanet.perfapp.data.dao.IperfProfileDao
import net.openmanet.perfapp.data.dao.IperfResultDao
import net.openmanet.perfapp.data.dao.NeighborSnapshotDao
import net.openmanet.perfapp.data.dao.NodeProfileDao
import net.openmanet.perfapp.data.dao.PingResultDao
import net.openmanet.perfapp.data.dao.TestSessionDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ManetDatabase =
        Room.databaseBuilder(context, ManetDatabase::class.java, "manet_perf.db")
            // Pre-1.0 schema churn expected, see ManetDatabase kdoc.
            .fallbackToDestructiveMigration(true)
            .build()

    @Provides
    fun provideNodeProfileDao(db: ManetDatabase): NodeProfileDao = db.nodeProfileDao()

    @Provides
    fun provideTestSessionDao(db: ManetDatabase): TestSessionDao = db.testSessionDao()

    @Provides
    fun provideNeighborSnapshotDao(db: ManetDatabase): NeighborSnapshotDao = db.neighborSnapshotDao()

    @Provides
    fun providePingResultDao(db: ManetDatabase): PingResultDao = db.pingResultDao()

    @Provides
    fun provideGpsFixDao(db: ManetDatabase): GpsFixDao = db.gpsFixDao()

    @Provides
    fun provideIperfResultDao(db: ManetDatabase): IperfResultDao = db.iperfResultDao()

    @Provides
    fun provideIperfProfileDao(db: ManetDatabase): IperfProfileDao = db.iperfProfileDao()
}
