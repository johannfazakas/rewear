package com.rewear.app.health

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

class HealthTest {

    @Test
    fun `given a running app when GET health then 200 and body contains the git sha`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            installHealth(BuildInfo(gitSha = "abc1234"))
        }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("abc1234"))
    }
}
