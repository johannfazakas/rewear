plugins {
    id("rewear.kotlin-common")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    alias(libs.plugins.graalvm.native)
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

graalvmNative {
    metadataRepository {
        enabled.set(true)
    }
    binaries {
        named("main") {
            imageName.set("rewear-api")
            buildArgs.add("--no-fallback")
            buildArgs.add("-Djava.awt.headless=true")
            buildArgs.add("-H:+ReportExceptionStackTraces")
            buildArgs.add(
                "--initialize-at-build-time=" +
                    "ch.qos.logback,org.slf4j," +
                    "kotlin.DeprecationLevel,kotlin.annotation.AnnotationRetention," +
                    "kotlin.annotation.AnnotationTarget,kotlin.coroutines.intrinsics.CoroutineSingletons",
            )
            // Reachability metadata: agent-generated set (com.rewear/app) unioned with a
            // hand-maintained safety net for Flyway plugins the agent run did not exercise
            // (com.rewear/extra). Referenced explicitly so loading does not depend on
            // classpath auto-detection.
            buildArgs.add(
                "-H:ReflectionConfigurationResources=" +
                    "META-INF/native-image/com.rewear/app/reflect-config.json," +
                    "META-INF/native-image/com.rewear/extra/reflect-config.json",
            )
            buildArgs.add(
                "-H:ResourceConfigurationResources=" +
                    "META-INF/native-image/com.rewear/app/resource-config.json," +
                    "META-INF/native-image/com.rewear/extra/resource-config.json",
            )
            buildArgs.add(
                "-H:SerializationConfigurationResources=" +
                    "META-INF/native-image/com.rewear/app/serialization-config.json",
            )
            buildArgs.add(
                "-H:JNIConfigurationResources=" +
                    "META-INF/native-image/com.rewear/app/jni-config.json",
            )
        }
    }
}
