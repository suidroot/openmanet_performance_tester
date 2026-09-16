package net.openmanet.perfapp.data.export

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.uploadSettingsDataStore by preferencesDataStore(name = "upload_settings")

/** Persists the default cloud-export endpoint URL set in Settings, so Export doesn't require
 * re-typing it every time - still overridable per-upload. */
@Singleton
class UploadSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyEndpointUrl = stringPreferencesKey("endpoint_url")

    val endpointUrl: Flow<String> = context.uploadSettingsDataStore.data.map { it[keyEndpointUrl] ?: "" }

    suspend fun setEndpointUrl(url: String) {
        context.uploadSettingsDataStore.edit { it[keyEndpointUrl] = url }
    }
}
