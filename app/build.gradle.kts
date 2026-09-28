import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.arjun.core_alert"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.arjun.core_alert"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    flavorDimensions += "distribution"

    productFlavors {
        create("uat") {
            dimension = "distribution"
            applicationIdSuffix = ".uat"
        }
        create("production") {
            dimension = "distribution"
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("../corealert-release.jks")
            storePassword = localProps.getProperty("STORE_PASSWORD", "")
            keyAlias = "corealert"
            keyPassword = localProps.getProperty("KEY_PASSWORD", "")
        }
    }

    buildTypes {
        debug {
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            vcsInfo {
                include = false
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation(project(":feature"))
    implementation(project(":core:domain"))
    implementation(project(":core:policy"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":core:monitoring"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)

    // Jetpack Compose (single-activity UI)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.bundles.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Dependency injection (Koin)
    implementation(libs.koin.android)

    // Logging
    implementation(libs.jakewharton.timber)

    testImplementation(libs.junit)
}
