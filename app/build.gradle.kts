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
        versionCode = 12
        versionName = "0.9.4"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
