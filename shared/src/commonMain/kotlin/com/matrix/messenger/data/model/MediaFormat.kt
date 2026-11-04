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
}
