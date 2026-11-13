plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.livecaption.persian"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.livecaption.persian"
        minSdk = 29
        targetSdk = 30
        versionCode = 2
        versionName = "0.4-floating-preview"
    }
}
