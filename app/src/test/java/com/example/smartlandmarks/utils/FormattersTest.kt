package com.example.smartlandmarks.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun `distance under one kilometre is shown in metres`() {
        assertEquals("123 m", Formatters.distance(123.45))
        assertEquals("999 m", Formatters.distance(999.0))
    }

    @Test
    fun `distance of a kilometre or more is shown in kilometres`() {
        assertEquals("1.00 km", Formatters.distance(1_000.0))
        assertEquals("2.35 km", Formatters.distance(2_345.6))
    }

    /** 812.35 m is the avg_distance from the API docs' sample response. */
    @Test
    fun `sample avg_distance from the API docs stays in metres`() {
        assertEquals("812 m", Formatters.distance(812.35))
    }

    /** A null distance means the server job has not resolved yet, not zero metres. */
    @Test
    fun `null distance renders as a dash rather than zero`() {
        assertEquals("—", Formatters.distance(null))
    }

    @Test
    fun `score is formatted to one decimal place`() {
        assertEquals("27.8", Formatters.score(27.8))
        assertEquals("5.0", Formatters.score(5.0))
    }
}
