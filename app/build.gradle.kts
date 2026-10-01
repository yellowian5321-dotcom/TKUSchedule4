plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    id("com.google.gms.google-services") version "4.4.4"
}

android {
    namespace = "com.example.tkuschedule"

    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.tkuschedule"

        minSdk = 26

        targetSdk {
            version = release(36)
        }

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility =
            JavaVersion.VERSION_11

        targetCompatibility =
            JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    /*
     * Android Core。
     */
    implementation(
        "androidx.core:core:1.17.0"
    )

    implementation(
        "androidx.core:core-ktx:1.17.0"
    )

    /*
     * 不再使用 libs.androidx.activity.compose。
     *
     * 原本版本太新，會要求 core 1.19.0，
     * 因此改成與 compileSdk 36 相容的版本。
     */
    implementation(
        "androidx.activity:activity-compose:1.11.0"
    )

    /*
     * Jetpack Compose。
     */
    implementation(
        platform(
            libs.androidx.compose.bom
        )
    )

    implementation(
        libs.androidx.compose.ui
    )

    implementation(
        libs.androidx.compose.ui.graphics
    )

    implementation(
        libs.androidx.compose.ui.tooling.preview
    )

    implementation(
        libs.androidx.compose.material3
    )

    /*
     * Lifecycle 與 ViewModel。
     */
    implementation(
        "androidx.lifecycle:" +
                "lifecycle-runtime-ktx:2.9.4"
    )

    implementation(
        "androidx.lifecycle:" +
                "lifecycle-viewmodel-ktx:2.9.4"
    )

    implementation(
        "androidx.lifecycle:" +
                "lifecycle-viewmodel-compose:2.9.4"
    )

    implementation(
        "androidx.lifecycle:" +
                "lifecycle-runtime-compose:2.9.4"
    )

    /*
     * 網路與課程資料。
     */
    implementation(
        "com.squareup.okhttp3:okhttp:4.12.0"
    )

    implementation(
        "org.jsoup:jsoup:1.23.2"
    )

    /*
     * Firebase AI Logic 與 Gemini。
     */
    implementation(
        platform(
            "com.google.firebase:firebase-bom:34.3.0"
        )
    )

    implementation(
        "com.google.firebase:firebase-ai"
    )

    /*
     * Firebase App Check。
     */
    implementation(
        "com.google.firebase:" +
                "firebase-appcheck-playintegrity"
    )

    debugImplementation(
        "com.google.firebase:" +
                "firebase-appcheck-debug"
    )

    /*
     * 單元測試。
     */
    testImplementation(
        libs.junit
    )

    androidTestImplementation(
        libs.androidx.junit
    )

    androidTestImplementation(
        libs.androidx.espresso.core
    )

    androidTestImplementation(
        platform(
            libs.androidx.compose.bom
        )
    )

    androidTestImplementation(
        libs.androidx.compose.ui.test.junit4
    )

    debugImplementation(
        libs.androidx.compose.ui.tooling
    )

    debugImplementation(
        libs.androidx.compose.ui.test.manifest
    )
}

/*
 * 防止其他相依套件把 Core 升到 1.19.0。
 */
configurations.all {
    resolutionStrategy {
        force(
            "androidx.core:core:1.17.0"
        )

        force(
            "androidx.core:core-ktx:1.17.0"
        )

        force(
            "androidx.activity:" +
                    "activity:1.11.0"
        )

        force(
            "androidx.activity:" +
                    "activity-compose:1.11.0"
        )
    }
}