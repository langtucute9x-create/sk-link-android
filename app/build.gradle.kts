plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "vn.sklight.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "vn.sklight.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 52
        versionName = "0.5.40"
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
    buildFeatures { compose = true }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
