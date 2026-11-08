plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.crewmate.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.crewmate.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.2.0-demo"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
