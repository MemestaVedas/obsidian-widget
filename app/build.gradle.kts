plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.obsidianwidget"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.obsidianwidget"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        viewBinding = true
    }
}

dependencies {
    // AndroidX Core KTX
    implementation("androidx.core:core-ktx:1.12.0")

    // AndroidX AppCompat
    implementation("androidx.appcompat:appcompat:1.6.1")

    // Material Design 3
    implementation("com.google.android.material:material:1.11.0")

    // Kotlin Coroutines (Android)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // AndroidX Lifecycle Runtime KTX
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")

    // AndroidX Work Runtime KTX (for background tasks)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // AndroidX DocumentFile (for SAF vault browsing)
    implementation("androidx.documentfile:documentfile:1.0.1")

    // AndroidX Activity KTX (for registerForActivityResult)
    implementation("androidx.activity:activity-ktx:1.8.2")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
