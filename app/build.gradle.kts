plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.valerie.meteoquote"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.valerie.meteoquote"
        minSdk = 24
        targetSdk = 35
        versionCode = 9
        versionName = "6"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            // Active la minification (R8) et le shrink des ressources
            isMinifyEnabled = true
            isShrinkResources = true

            // Fichiers de règles ProGuard/R8
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Coroutines pour l'appel réseau en arrière-plan
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // Simple JSON
    implementation("org.json:json:20240303")
}
