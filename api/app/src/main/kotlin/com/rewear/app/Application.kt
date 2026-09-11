package com.rewear.app

import com.rewear.app.config.AppConfig
import com.rewear.app.db.Database
import com.rewear.app.db.runMigrations
import com.rewear.app.di.appModule
import com.rewear.app.health.BuildInfo
import com.rewear.app.health.installHealth
import com.rewear.app.spike.installResizeSpike
import com.rewear.catalog.catalogModule
import com.rewear.catalog.categories.CategoryQueries
import com.rewear.catalog.favorites.FavoriteQueries
import com.rewear.catalog.listings.ListingQueries
import com.rewear.users.UserQueries
import com.rewear.users.usersModule
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import org.koin.ktor.ext.getKoin
import org.koin.ktor.plugin.Koin
import kotlin.system.exitProcess

fun main() {
    System.setProperty("java.awt.headless", "true")
    try {
        val config = AppConfig.fromEnv()
        val buildInfo = BuildInfo.fromEnv()
        runMigrations(config.database)
        val database = Database.connect(config.database)
        embeddedServer(CIO, port = config.port) {
            module(config, buildInfo, database)
        }.start(wait = true)
    } catch (cause: Exception) {
        System.err.println("Failed to start rewear-api: ${cause.message}")
        exitProcess(1)
    }
}

fun Application.module(config: AppConfig, buildInfo: BuildInfo, database: Database) {
    install(ContentNegotiation) { json() }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(
                text = "Internal error: ${cause.stackTraceToString()}",
                status = HttpStatusCode.InternalServerError,
            )
        }
    }
    install(Koin) {
        modules(appModule(database), usersModule, catalogModule)
    }
    verifyGraph()
    installHealth(buildInfo)
    installResizeSpike()
}

private fun Application.verifyGraph() {
    val koin = getKoin()
    koin.get<Database>()
    koin.get<UserQueries>()
    koin.get<ListingQueries>()
    koin.get<CategoryQueries>()
    koin.get<FavoriteQueries>()
}
