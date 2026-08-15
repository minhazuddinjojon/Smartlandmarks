package com.example.smartlandmarks.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreColorTest {

    @Test
    fun `normalise maps the lowest score to zero and the highest to one`() {
        assertEquals(0f, ScoreColor.normalise(10.0, 10.0, 50.0), 0.001f)
        assertEquals(1f, ScoreColor.normalise(50.0, 10.0, 50.0), 0.001f)
    }

    @Test
    fun `normalise puts a mid score in the middle`() {
        assertEquals(0.5f, ScoreColor.normalise(30.0, 10.0, 50.0), 0.001f)
    }

    /**
     * Guards the per-student-key case: if every landmark shares one score the range is
     * degenerate and a naive implementation divides by zero.
     */
    @Test
    fun `normalise handles a degenerate range without dividing by zero`() {
        assertEquals(0.5f, ScoreColor.normalise(27.8, 27.8, 27.8), 0.001f)
        assertEquals(0.5f, ScoreColor.normalise(0.0, 0.0, 0.0), 0.001f)
    }

    @Test
    fun `normalise clamps scores outside the observed range`() {
        assertEquals(0f, ScoreColor.normalise(-5.0, 10.0, 50.0), 0.001f)
        assertEquals(1f, ScoreColor.normalise(500.0, 10.0, 50.0), 0.001f)
    }

    @Test
    fun `colours differ across the ramp`() {
        val low = ScoreColor.forScore(0.0, 0.0, 100.0)
        val mid = ScoreColor.forScore(50.0, 0.0, 100.0)
        val high = ScoreColor.forScore(100.0, 0.0, 100.0)
        assertTrue(low != mid && mid != high && low != high)
    }
}
