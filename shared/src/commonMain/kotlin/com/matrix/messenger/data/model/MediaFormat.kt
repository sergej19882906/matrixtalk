package com.matrix.messenger.data.model

/** Media helpers shared by the timeline. */
object MediaFormat {

    /** "1:23" style duration; null-safe for unknown lengths. */
    fun duration(ms: Long?): String {
        if (ms == null || ms < 0) return "--:--"
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes >= 60) {
            val hours = minutes / 60
            "%d:%02d:%02d".format(hours, minutes % 60, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    /** Downsamples raw peak amplitudes into a fixed number of bars. */
    fun normalizeWaveform(raw: List<Int>, bins: Int = 32): List<Int> {
        if (raw.isEmpty()) return List(bins) { 8 }
        if (raw.size <= bins) {
            val out = raw.toList()
            return out + List(bins - out.size) { 8 }
        }
        val step = raw.size.toDouble() / bins
        return List(bins) { i ->
            val from = (i * step).toInt()
            val to = (((i + 1) * step).toInt()).coerceAtLeast(from + 1)
            val slice = raw.subList(from, to.coerceAtMost(raw.size))
            (slice.maxOrNull() ?: 8).coerceIn(8, 1024)
        }
    }
}
