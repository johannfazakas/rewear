plugins {
    id("rewear.kotlin-common")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":users"))
    implementation(libs.koin.core)
}
