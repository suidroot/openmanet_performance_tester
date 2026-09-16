package net.openmanet.perfapp.core

import javax.inject.Inject
import javax.inject.Singleton

/** Seam for injectable time so components that stamp records are testable without wall-clock flakiness. */
interface AppClock {
    fun nowMs(): Long
}

@Singleton
class SystemAppClock @Inject constructor() : AppClock {
    override fun nowMs(): Long = System.currentTimeMillis()
}
