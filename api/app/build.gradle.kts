plugins {
    id("rewear.kotlin-common")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
}

application {
    mainClass.set("com.rewear.app.ApplicationKt")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":users"))
    implementation(project(":catalog"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.status.pages)

    implementation(libs.koin.core)
    implementation(libs.koin.ktor)

    implementation(libs.exposed.core)
    implementation(libs.exposed.r2dbc)
    implementation(libs.r2dbc.postgresql)
    implementation(libs.r2dbc.pool)
    implementation(libs.postgresql)

    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)

    implementation(libs.thumbnailator)
    implementation(libs.logback.classic)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.koin.test)
    testImplementation(libs.konsist)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
}
