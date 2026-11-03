package com.matrix.messenger.data.model

/**
 * A geographic point shared as an m.location message (Element X style).
 */
data class GeoLocation(
    val latitude: Double,
    val longitude: Double,
    /** Accuracy radius in meters as advertised by the sender, null when unknown. */
    val accuracyMeters: Double? = null
)

/**
 * Parser and helpers for `geo:` URIs (RFC 5870 subset used by Matrix) and
 * OpenStreetMap deep links / static tiles.
 */
object GeoUri {

    private val COORDS = Regex("^(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)$")

    /** Parses `geo:55.751244,37.618423;u=35` — null when malformed. */
    fun parse(uri: String): GeoLocation? {
        if (!uri.startsWith("geo:", ignoreCase = true)) return null
        val parts = uri.substring(4).split(';')
        val coords = parts.firstOrNull { COORDS.matches(it) }
            ?: parts.firstOrNull { it.contains(',') }
            ?: return null
        val match = COORDS.matchEntire(coords.trim()) ?: return null
        val latitude = match.groupValues[1].toDoubleOrNull() ?: return null
        val longitude = match.groupValues[2].toDoubleOrNull() ?: return null
        if (latitude < -90.0 || latitude > 90.0 || longitude < -180.0 || longitude > 180.0) return null
        val accuracy = parts.firstOrNull { it.trim().startsWith("u=", ignoreCase = true) }
            ?.substringAfter('=')?.trim()?.toDoubleOrNull()
        return GeoLocation(latitude, longitude, accuracy)
    }

    fun toGeoUri(location: GeoLocation): String = buildString {
        append("geo:")
        append(location.latitude)
        append(",")
        append(location.longitude)
        location.accuracyMeters?.let { append(";u="); append(it) }
    }

    /** Human-readable coordinates, e.g. `55.7512°, 37.6184°` (locale-independent). */
    fun format(location: GeoLocation): String =
        "${fmt(location.latitude)}°, ${fmt(location.longitude)}°"

    private fun fmt(value: Double): String =
        (kotlin.math.round(value * 10000) / 10000.0).toString()

    /** OpenStreetMap link that centers the map on the point with a marker. */
    fun toOsmUrl(location: GeoLocation): String {
        val zoom = 16
        return "https://www.openstreetmap.org/?mlat=${location.latitude}&mlon=${location.longitude}#map=$zoom/${location.latitude}/${location.longitude}"
    }

    /** OSM raster tile containing the point (slippy map tiling scheme). */
    fun toTileUrl(location: GeoLocation, zoom: Int = 15): String? {
        if (zoom < 0 || zoom > 19) return null
        if (location.latitude < -85.05112878 || location.latitude > 85.05112878) return null
        val latRad = Math.toRadians(location.latitude)
        val n = 1 shl zoom
        val x = ((location.longitude + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
        val y = ((1.0 - ln(tan(latRad) + sec(latRad)) / Math.PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
        return "https://tile.openstreetmap.org/$zoom/$x/$y.png"
    }

    private fun ln(value: Double): Double = kotlin.math.ln(value)
    private fun tan(value: Double): Double = kotlin.math.tan(value)
    private fun sec(value: Double): Double = 1.0 / kotlin.math.cos(value)
}
