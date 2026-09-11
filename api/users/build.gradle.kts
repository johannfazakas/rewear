plugins {
    id("rewear.kotlin-common")
}

dependencies {
    implementation(project(":common"))
    implementation(libs.koin.core)
}
