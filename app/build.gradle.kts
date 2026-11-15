plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "dev.local.streamerremote"
    compileSdk = 37
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    defaultConfig { applicationId = "dev.local.streamerremote"; minSdk = 26; targetSdk = 32; versionCode = 17; versionName = "0.19" }
    val signingFile = System.getenv("STREAMER_REMOTE_SIGNING_FILE")
    if (!signingFile.isNullOrBlank()) {
        signingConfigs {
            create("stableDebug") {
                storeFile = file(signingFile)
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
                storeType = "PKCS12"
            }
        }
        buildTypes.getByName("debug").signingConfig = signingConfigs.getByName("stableDebug")
    }
}
dependencies {
    implementation("com.flyfishxu:kadb:2.1.4")
}
