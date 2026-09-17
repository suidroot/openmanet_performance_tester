package net.openmanet.perfapp.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.openmanet.perfapp.data.entities.GpsSource
import javax.inject.Inject
import javax.inject.Singleton

private val Context.gpsPreferenceDataStore by preferencesDataStore(name = "gps_preference")

/**
 * Which GPS source the dashboard prefers to display when both have produced a fix: the device's
 * own GPS or the mesh's CoT multicast feed (GpsSource.NODE_GNSS isn't offered - nothing in this
 * app collects it). Defaults to DEVICE since it's always available once permission is granted,
 * unlike CoT which depends on another unit actively broadcasting.
 */
@Singleton
class GpsPreferenceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val key = stringPreferencesKey("preferred_gps_source")

    val preferredSource: Flow<GpsSource> = context.gpsPreferenceDataStore.data
        .map { prefs -> prefs[key]?.let { runCatching { GpsSource.valueOf(it) }.getOrNull() } ?: GpsSource.DEVICE }

    suspend fun setPreferredSource(source: GpsSource) {
        context.gpsPreferenceDataStore.edit { it[key] = source.name }
    }
}
