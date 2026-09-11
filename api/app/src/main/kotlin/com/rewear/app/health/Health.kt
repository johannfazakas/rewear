package com.rewear.app.health

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

data class BuildInfo(val gitSha: String) {
    companion object {
        fun fromEnv(env: (String) -> String? = System::getenv): BuildInfo =
            BuildInfo(gitSha = env("GIT_SHA")?.takeIf { it.isNotBlank() } ?: "unknown")
    }
}

@Serializable
data class HealthResponse(val status: String, val gitSha: String)

fun Application.installHealth(buildInfo: BuildInfo) {
    routing {
        get("/health") {
            call.respond(HttpStatusCode.OK, HealthResponse(status = "UP", gitSha = buildInfo.gitSha))
        }
    }
}
