plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "vn.sklight.auto"
    compileSdk = 34

    defaultConfig {
        applicationId = "vn.sklight.auto"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
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
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.car.app:app:1.4.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
}
