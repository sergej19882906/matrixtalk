package com.matrix.messenger.data.model

import kotlin.test.Test
import kotlin.test.assertEquals

class MediaFormatTest {

    @Test
    fun secondsOnly() {
        assertEquals("0:07", MediaFormat.duration(7000))
    }

    @Test
    fun minutesAndSeconds() {
        assertEquals("1:23", MediaFormat.duration(83000))
    }

    @Test
    fun overAnHour() {
        assertEquals("1:02:05", MediaFormat.duration(3725000))
    }

    @Test
    fun unknownLength() {
        assertEquals("--:--", MediaFormat.duration(null))
        assertEquals("--:--", MediaFormat.duration(-1))
    }
}
