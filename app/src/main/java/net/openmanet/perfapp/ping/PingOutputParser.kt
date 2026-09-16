package net.openmanet.perfapp.ping

import net.openmanet.perfapp.data.entities.PingResult

/**
 * Parses `/system/bin/ping -c 1` output. Deliberately defensive: Android's ping binary varies
 * by vendor/AOSP version (toybox vs. busybox vs. iputils-derived builds), so rather than
 * requiring an exact format match, this looks for a `time=<ms>` token anywhere in the output
 * and treats its absence as a failure (timeout/unreachable/unparseable) rather than crashing.
 * The full raw output is kept on the result for debugging format mismatches on real devices.
 */
object PingOutputParser {
    private val TIME_REGEX = Regex("""time[=<]\s*([0-9]+(?:\.[0-9]+)?)\s*ms""")

    fun parse(sessionId: String, targetHost: String, timestampMs: Long, rawOutput: String): PingResult {
        val match = TIME_REGEX.find(rawOutput)
        val rttMs = match?.groupValues?.get(1)?.toDoubleOrNull()
        return PingResult(
            sessionId = sessionId,
            timestampMs = timestampMs,
            targetHost = targetHost,
            targetLabel = null,
            rttMs = rttMs,
            success = rttMs != null,
            rawOutputLine = rawOutput.trim().takeLast(MAX_RAW_LEN),
        )
    }

    private const val MAX_RAW_LEN = 500
}
