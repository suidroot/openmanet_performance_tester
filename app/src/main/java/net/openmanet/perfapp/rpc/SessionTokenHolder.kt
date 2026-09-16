package net.openmanet.perfapp.rpc

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the Bearer token issued by openmanetd's POST /auth/login (see AuthRepository) for the
 * lifetime of the current connection. openmanetd's auth middleware requires this token on every
 * API call except DashboardService.GetDashboardStatus and the SetupService wizard endpoints -
 * see OpenMANET/openmanetd internal/auth/middleware.go, confirmed against a real node (it
 * returns 401 {"error":"unauthorized"} without it). AuthHeaderInterceptor reads this to attach
 * the header automatically so individual repositories don't each have to thread it through.
 */
@Singleton
class SessionTokenHolder @Inject constructor() {
    @Volatile
    var token: String? = null
}
