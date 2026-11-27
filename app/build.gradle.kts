plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "vn.sklight.driver"
    compileSdk = 34

    defaultConfig {
        applicationId = "vn.sklight.driver"
        minSdk = 29
        targetSdk = 34
        versionCode = 3
        versionName = "0.3.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // Co khoa rieng (GitHub Secrets) thi ky bang khoa do, khong thi dung khoa debug.
    val ksPath = System.getenv("SKL_KEYSTORE")
    val ksPass = System.getenv("SKL_PASS")
    signingConfigs {
        if (ksPath != null && ksPass != null && file(ksPath).exists()) {
            create("fixed") {
                storeFile = file(ksPath)
                storePassword = ksPass
                keyAlias = "sklink"
                keyPassword = ksPass
            }
        }
    }
    buildTypes {
        release {
            signingConfigs.findByName("fixed")?.let { signingConfig = it }
                ?: run { signingConfig = signingConfigs.getByName("debug") }
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    androidResources { noCompress += "tflite" }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    // Nhan dien vat the chay tren may (MediaPipe Tasks, giay phep Apache-2.0)
    implementation("com.google.mediapipe:tasks-vision:0.10.14")
}
