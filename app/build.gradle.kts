import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.kapt")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.salarycalc_trackincomeandexpense"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.salarycalc_trackincomeandexpense"
        minSdk = 26
        targetSdk = 36
        versionCode = 18
        versionName = "2.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val props = Properties().apply {
                val f = rootProject.file("keystore.properties")
                if (f.exists()) {
                    f.inputStream().use { load(it) }
                }
            }
            storeFile = file(props.getProperty("storeFile", "C:/Users/appua/salarycal.jks"))
            storePassword = props.getProperty("storePassword", "aromal0497h")
            keyAlias = props.getProperty("keyAlias", "key0")
            keyPassword = props.getProperty("keyPassword", "aromal0497h")
        }
    }

    buildTypes {
        debug {
            // Sign debug builds with the release keystore so running from
            // Android Studio installs over the existing app (no uninstall /
            // data loss when the device already has the signed APK).
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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
    implementation(libs.androidx.activity.compose)

    // ✅ FIX FOR LINT ERROR (ActivityResult / Fragment version issue)
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.8.6")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.animation:animation")

    // PDF bank statement parsing (pdfbox-android)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    
    // Room DB
    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.8.1")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ML Kit on-device OCR for scanned / image-only bank statement PDFs
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // Timber logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}