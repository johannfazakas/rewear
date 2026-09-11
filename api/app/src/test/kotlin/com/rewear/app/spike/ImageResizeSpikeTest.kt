package com.rewear.app.spike

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageResizeSpikeTest {

    @Test
    fun `given the sample photo when resized then variants are valid images of the expected dimensions`() {
        val variants = ImageResizeSpike.resizeSample()

        assertEquals(setOf("thumbnail", "detail"), variants.map { it.name }.toSet())

        val thumbnail = variants.first { it.name == "thumbnail" }
        val detail = variants.first { it.name == "detail" }

        assertEquals(150, maxOf(thumbnail.width, thumbnail.height))
        assertEquals(800, maxOf(detail.width, detail.height))
        assertTrue(thumbnail.bytes.isNotEmpty())
        assertTrue(detail.bytes.isNotEmpty())
    }
}
