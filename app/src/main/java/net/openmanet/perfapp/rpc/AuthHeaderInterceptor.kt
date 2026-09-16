package net.openmanet.perfapp.rpc

import okhttp3.Interceptor
import okhttp3.Response

/** Attaches `Authorization: Bearer <token>` to every request once SessionTokenHolder has one. */
class AuthHeaderInterceptor(
    private val sessionTokenHolder: SessionTokenHolder,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = sessionTokenHolder.token
        val request = if (token != null) {
            chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
