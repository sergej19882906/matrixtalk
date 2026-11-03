package com.matrix.messenger.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeoUriTest {

    @Test
    fun parseSimple() {
        val loc = GeoUri.parse("geo:55.751244,37.618423")
        assertNotNull(loc)
        assertEquals(55.751244, loc.latitude, 1e-9)
        assertEquals(37.618423, loc.longitude, 1e-9)
        assertEquals(null, loc.accuracyMeters)
    }

    @Test
    fun parseWithAccuracy() {
        val loc = GeoUri.parse("geo:55.75,37.62;u=35.0")
        assertNotNull(loc)
        assertEquals(35.0, loc.accuracyMeters!!, 1e-9)
    }

    @Test
    fun parseCaseInsensitivePrefix() {
        assertNotNull(GeoUri.parse("GEO:10.0,20.0"))
    }

    @Test
    fun parseRoundTrip() {
        val original = GeoLocation(48.8566, 2.3522, accuracyMeters = 12.0)
        val parsed = GeoUri.parse(GeoUri.toGeoUri(original))!!
        assertEquals(original.latitude, parsed.latitude, 1e-9)
        assertEquals(original.accuracyMeters!!, parsed.accuracyMeters!!, 1e-9)
    }

    @Test
    fun rejectsMalformed() {
        assertNull(GeoUri.parse("not a geo uri"))
        assertNull(GeoUri.parse("geo:"))
        assertNull(GeoUri.parse("geo:abc,def"))
        assertNull(GeoUri.parse("geo:99.0,10.0")) // latitude out of range
        assertNull(GeoUri.parse("geo:10.0,190.0")) // longitude out of range
    }

    @Test
    fun osmUrlContainsMarker() {
        val url = GeoUri.toOsmUrl(GeoLocation(52.52, 13.405))
        assertTrue(url.startsWith("https://www.openstreetmap.org/?mlat=52.52"))
        assertTrue(url.contains("mlon=13.405"))
        assertTrue(url.contains("#map="))
    }

    @Test
    fun tileUrlMath() {
        // Berlin (52.52, 13.405) at zoom 15 lands on tile x=17605, y=10743.
        val url = GeoUri.toTileUrl(GeoLocation(52.52, 13.405), zoom = 15)
        assertEquals("https://tile.openstreetmap.org/15/17604/10746.png", url)
        // Equator/prime meridian is the top-left tile quadrant center.
        assertEquals("https://tile.openstreetmap.org/2/2/1.png", GeoUri.toTileUrl(GeoLocation(45.0, 45.0), zoom = 2))
        assertNull(GeoUri.toTileUrl(GeoLocation(0.0, 0.0), zoom = 25))
    }

    @Test
    fun formatIsReadable() {
        val formatted = GeoUri.format(GeoLocation(55.751244, 37.618423))
        assertEquals("55.7512°, 37.6184°", formatted)
    }
}
