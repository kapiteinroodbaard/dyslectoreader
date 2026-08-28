plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Semantic version — single source of truth, shown in the About screen.
val appVersion = "1.0.0"

fun semverToCode(v: String): Int {
    val (major, minor, patch) = v.split(".").map { it.toInt() }
    return major * 1_000_000 + minor * 1_000 + patch
}

android {
    namespace = "org.dyslectoreader"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.dyslectoreader"
        minSdk = 36
        targetSdk = 36
        versionCode = semverToCode(appVersion)
        versionName = appVersion
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    implementation(libs.androidx.datastore.preferences)
}
