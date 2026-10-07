plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val rawBaseUrl = (project.findProperty("BASE_URL") as String?) ?: "https://ltv.860527.xyz:88"

android {
    namespace = "com.moontvplus.tvapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.moontvplus.tvapp"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "BASE_URL", "\"$rawBaseUrl\"")
        resValue("string", "app_name", "MoonTV Plus TV")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
    buildTypes {
        release { isMinifyEnabled = false }
    }
}

dependencies {
    implementation("com.google.android.exoplayer:exoplayer-core:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-hls:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.19.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
