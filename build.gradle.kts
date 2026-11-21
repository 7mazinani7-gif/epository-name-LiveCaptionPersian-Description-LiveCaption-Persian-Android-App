
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


dependencies {
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.6")
    // Offline Persian speech recognition (native Android / arm64 supported)
    implementation("com.alphacephei:vosk-android:0.3.75")
}
