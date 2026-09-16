package net.openmanet.perfapp.rpc

import android.content.Context
import android.net.ConnectivityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Logs in against openmanetd's real auth endpoint - POST /auth/login, PAM-backed (the same
 * credentials as the device's OpenWrt/LuCI admin login), confirmed against a real node and
 * OpenMANET/openmanetd's internal/auth/handlers.go. This is plain REST (JSON in/out), not a
 * Connect-RPC call, but lives on the same host:8087 as the RPC API and needs the same h2c
 * cleartext + active-network binding as OpenManetClientFactory.
 *
 * On success, stores the returned Bearer token in SessionTokenHolder, which
 * AuthHeaderInterceptor then attaches to every subsequent RPC call automatically.
 */
@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionTokenHolder: SessionTokenHolder,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private fun httpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder().connectionSpecs(listOf(ConnectionSpec.CLEARTEXT))
        connectivityManager.activeNetwork?.let { builder.socketFactory(it.socketFactory) }
        return builder.build()
    }

    suspend fun login(nodeIp: String, username: String, password: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("username", username)
                    put("password", password)
                }.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("http://$nodeIp:${OpenManetClientFactory.DEFAULT_PORT}/auth/login")
                    .post(body)
                    .build()

                httpClient().newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val message = runCatching { JSONObject(responseBody).getString("error") }
                            .getOrDefault("Login failed (HTTP ${response.code})")
                        return@withContext Result.failure(IOException(message))
                    }
                    val token = JSONObject(responseBody).getString("token")
                    sessionTokenHolder.token = token
                    Result.success(token)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Best-effort server-side session teardown; the client always forgets the token locally
     * regardless of whether this succeeds (see ConnectionViewModel.disconnect). */
    suspend fun logout(nodeIp: String) {
        val token = sessionTokenHolder.token ?: return
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("http://$nodeIp:${OpenManetClientFactory.DEFAULT_PORT}/auth/logout")
                    .header("Authorization", "Bearer $token")
                    .post("".toRequestBody(null))
                    .build()
                httpClient().newCall(request).execute().close()
            } catch (e: Exception) {
                // Best-effort - the local token is cleared regardless.
            }
        }
    }
}
