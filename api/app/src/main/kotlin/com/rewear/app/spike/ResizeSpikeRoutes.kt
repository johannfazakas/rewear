package com.rewear.app.spike

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable
data class ResizeVariantReport(val name: String, val width: Int, val height: Int, val bytes: Int)

@Serializable
data class ResizeCheckResponse(val ok: Boolean, val variants: List<ResizeVariantReport>)

fun Application.installResizeSpike() {
    routing {
        get("/internal/resize-check") {
            val variants = ImageResizeSpike.resizeSample().map {
                ResizeVariantReport(name = it.name, width = it.width, height = it.height, bytes = it.bytes.size)
            }
            call.respond(HttpStatusCode.OK, ResizeCheckResponse(ok = true, variants = variants))
        }
    }
}
