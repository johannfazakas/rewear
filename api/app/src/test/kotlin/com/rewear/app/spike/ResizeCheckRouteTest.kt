package com.rewear.app.spike

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResizeCheckRouteTest {

    @Test
    fun `given a running app when GET resize-check then 200 and body reports ok`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            installResizeSpike()
        }

        val response = client.get("/internal/resize-check")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"ok\":true"))
    }
}
