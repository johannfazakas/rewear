package com.rewear.app.spike

import net.coobird.thumbnailator.Thumbnails
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

object ImageResizeSpike {
    data class VariantSpec(val name: String, val maxEdge: Int)

    data class Variant(val name: String, val width: Int, val height: Int, val bytes: ByteArray)

    val specs = listOf(
        VariantSpec(name = "thumbnail", maxEdge = 150),
        VariantSpec(name = "detail", maxEdge = 800),
    )

    fun resize(input: ByteArray): List<Variant> = specs.map { spec ->
        val output = ByteArrayOutputStream()
        Thumbnails.of(ByteArrayInputStream(input))
            .size(spec.maxEdge, spec.maxEdge)
            .outputFormat("jpg")
            .toOutputStream(output)
        val bytes = output.toByteArray()
        val decoded = ImageIO.read(ByteArrayInputStream(bytes))
            ?: error("Resized ${spec.name} variant is not a decodable image")
        Variant(name = spec.name, width = decoded.width, height = decoded.height, bytes = bytes)
    }

    fun resizeSample(): List<Variant> {
        val sample = javaClass.getResourceAsStream("/sample.jpg")
            ?: error("Sample image /sample.jpg not found on the classpath")
        return resize(sample.readBytes())
    }
}
