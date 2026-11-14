plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "dev.local.streamerremote"
    compileSdk = 35
    defaultConfig { applicationId = "dev.local.streamerremote"; minSdk = 26; targetSdk = 32; versionCode = 1; versionName = "0.1" }
}
dependencies {
    implementation("com.flyfishxu:kadb:2.1.4")
}
