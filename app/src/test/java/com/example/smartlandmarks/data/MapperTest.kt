package com.example.smartlandmarks.data

import com.example.smartlandmarks.data.mapper.toEntityOrNull
import com.example.smartlandmarks.data.remote.dto.LandmarkDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapperTest {

    private fun dto(
        id: Int? = 3,
        title: String? = "Saint Martin's Island",
        lat: Double? = 20.6229,
        lon: Double? = 92.322,
        isActive: Int? = 1,
        score: Double? = 27.8
    ) = LandmarkDto(
        id = id, title = title, lat = lat, lon = lon,
        image = "uploads/1786430640_5629.jpg", isActive = isActive,
        visitCount = 4, avgDistance = 812.35, score = score
    )

    /** The exact sample payload from the API reference must map cleanly. */
    @Test
    fun `documented sample response maps to a complete entity`() {
        val entity = dto().toEntityOrNull(cachedAt = 1_000L)!!
        assertEquals(3, entity.id)
        assertEquals("Saint Martin's Island", entity.title)
        assertEquals(20.6229, entity.latitude, 0.00001)
        assertEquals(27.8, entity.score, 0.001)
        assertEquals(4, entity.visitCount)
        assertTrue(entity.isActive)
    }

    @Test
    fun `rows without an id or coordinates are dropped rather than stored half-formed`() {
        assertNull(dto(id = null).toEntityOrNull(0L))
        assertNull(dto(lat = null).toEntityOrNull(0L))
        assertNull(dto(lon = null).toEntityOrNull(0L))
    }

    @Test
    fun `missing optional fields fall back to safe defaults`() {
        val entity = dto(title = null, score = null).toEntityOrNull(0L)!!
        assertEquals("Untitled landmark", entity.title)
        assertEquals(0.0, entity.score, 0.001)
    }

    @Test
    fun `is_active zero marks a soft-deleted landmark`() {
        assertTrue(!dto(isActive = 0).toEntityOrNull(0L)!!.isActive)
    }

    /** get_landmarks only returns active rows, so an absent flag means active. */
    @Test
    fun `absent is_active defaults to active`() {
        assertTrue(dto(isActive = null).toEntityOrNull(0L)!!.isActive)
    }
}
