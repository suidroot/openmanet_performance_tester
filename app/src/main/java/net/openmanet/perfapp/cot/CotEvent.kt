package net.openmanet.perfapp.cot

/** A decoded Cursor-on-Target &lt;event&gt; stanza - just the fields this app cares about. */
data class CotEvent(
    val uid: String,
    val lat: Double,
    val lon: Double,
    val hae: Double?,
    val speed: Double?,
    val course: Double?,
    /** Raw `time` attribute (ISO-8601), if present and parseable. */
    val timeMs: Long?,
)
