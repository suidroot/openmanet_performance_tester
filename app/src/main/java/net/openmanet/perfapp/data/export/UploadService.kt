package net.openmanet.perfapp.data.export

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.openmanet.perfapp.di.CloudHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uploads an exported CSV to a user-supplied HTTP(S) endpoint (e.g. a presigned S3 PUT URL, or
 * any REST endpoint that accepts a raw body PUT). Deliberately uses the CloudHttpClient - never
 * bound to the mesh Network (see di/NetworkModule) - since the mesh is typically offline-only
 * and this needs whatever network actually reaches the internet (usually cellular), while ping/
 * CoT/iperf keep using the mesh concurrently via their own network-bound clients.
 */
@Singleton
class UploadService @Inject constructor(
    @CloudHttpClient private val httpClient: OkHttpClient,
) {
    suspend fun upload(file: File, endpointUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(endpointUrl)
                .put(file.asRequestBody(CSV_MEDIA_TYPE))
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("Upload failed: HTTP ${response.code} ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private val CSV_MEDIA_TYPE = "text/csv".toMediaType()
    }
}
