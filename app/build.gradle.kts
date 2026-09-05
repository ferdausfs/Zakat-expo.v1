import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget


plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0"
}

android {
    namespace = "com.ritesh.cashiro"
    compileSdk = 36
    
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        applicationId = "com.ritesh.cashiro"
        minSdk = 26
        targetSdk = 36
        versionCode = 102
        versionName = "2.1.69-beta"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Load RSA public key from local.properties
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            val localProperties = Properties()
            localProperties.load(localPropertiesFile.inputStream())
            
            val rsaPublicKey = localProperties.getProperty("RSA_PUBLIC_KEY", "")
            buildConfigField("String", "RSA_PUBLIC_KEY", "\"$rsaPublicKey\"")
        } else {
            // Fallback empty key for CI/CD builds
            buildConfigField("String", "RSA_PUBLIC_KEY", "\"\"")
        }
        

    }


    signingConfigs {
        create("release") {
            // Resolution order for the release keystore:
            //   1. Environment variables (CI — GitHub Actions release secrets:
            //      KEYSTORE_FILE / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD)
            //   2. local.properties (local dev: RELEASE_STORE_FILE / etc.)
            //   3. Neither present -> storeFile stays null and the release
            //      build type falls back to DEBUG signing so local builds
            //      still produce an installable APK. CI release builds always
            //      use the stable keystore via (1), so every release APK is
            //      signed with the same key and upgrades in place.
            val envStoreFile = System.getenv("KEYSTORE_FILE")
            val envStorePassword = System.getenv("KEYSTORE_PASSWORD")
            val envKeyAlias = System.getenv("KEY_ALIAS")
            val envKeyPassword = System.getenv("KEY_PASSWORD")

            val localPropertiesFile = rootProject.file("local.properties")
            var localStoreFile = ""
            var localStorePassword = ""
            var localKeyAlias = ""
            var localKeyPassword = ""
            if (localPropertiesFile.exists()) {
                val localProperties = Properties()
                localProperties.load(localPropertiesFile.inputStream())
                localStoreFile = localProperties.getProperty("RELEASE_STORE_FILE", "")
                localStorePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD", "")
                localKeyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS", "")
                localKeyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD", "")
            }

            when {
                !envStoreFile.isNullOrEmpty() && !envStorePassword.isNullOrEmpty() &&
                        !envKeyAlias.isNullOrEmpty() && !envKeyPassword.isNullOrEmpty() -> {
                    storeFile = file(envStoreFile)
                    storePassword = envStorePassword
                    keyAlias = envKeyAlias
                    keyPassword = envKeyPassword
                }
                localStoreFile.isNotEmpty() -> {
                    storeFile = file(localStoreFile)
                    storePassword = localStorePassword
                    keyAlias = localKeyAlias
                    keyPassword = localKeyPassword
                }
                // else: leave unset -> debug-signing fallback in buildTypes
            }
        }
    }
    
    flavorDimensions += "version"
    productFlavors {
        create("fdroid") {
            dimension = "version"
            // F-Droid builds will use their own signing
            // Only include ARM architectures for F-Droid (no x86 emulator support)
            ndk {
                abiFilters += setOf("arm64-v8a", "armeabi-v7a")
            }
        }
        create("standard") {
            dimension = "version"
            isDefault = true
            // Standard flavor includes all architectures (including x86 for emulators)
        }
    }

    splits {
        abi {
            // Disable splits for bundle builds (AABs) and fdroid flavor builds.
            // F-Droid expects exactly one APK output — splits cause the build to fail.
            //noinspection WrongGradleMethod
            val runTasks = gradle.startParameter.taskNames.map { it.lowercase() }
            //noinspection WrongGradleMethod
            val isBundleBuild = runTasks.any { it.contains("bundle") }
            //noinspection WrongGradleMethod
            val isFdroidBuild = runTasks.any { it.contains("fdroid") }

            isEnable = !isBundleBuild && !isFdroidBuild

            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }


    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use the real release keystore when configured (CI secrets or
            // local.properties); otherwise fall back to debug signing so a
            // local dev build is still installable. CI release builds must
            // always be keystore-signed for consistent, upgrade-in-place APKs.
            val releaseConfig = signingConfigs.getByName("release")
            signingConfig = if (releaseConfig.storeFile != null) releaseConfig
            else signingConfigs.getByName("debug")
            
            // Include debug symbols for native crashes
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            freeCompilerArgs.add("-Xannotation-default-target=param-property")
        }
    }

    buildFeatures {
        compose = true
    }

    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            // Required for Robolectric (JVM) Compose UI tests
            isIncludeAndroidResources = true
            all { test ->
                test.maxHeapSize = "1792m"
            }
        }
    }

    // Expose the committed Room schema history (app/schemas/) to JVM
    // migration tests. Robolectric reads assets from the APP's merged
    // debug assets (see test_config.properties -> android_merged_assets),
    // so the schemas must be a debug-variant asset for MigrationTestHelper
    // to find schemas/<db-class>/<version>.json. Debug-only: release APKs
    // do not include these.
    sourceSets {
        getByName("debug") {
            assets.srcDir("$projectDir/schemas")
        }
    }
}


// Configure Room schema export
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.compose.animation)

    // Local modules
    implementation(project(":parser-core"))
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    
    // Color Picker for Compose
    implementation(libs.colorpicker.compose)
    implementation(libs.haze)
    
    // Splash Screen API
    implementation(libs.androidx.core.splashscreen)
    
    // Lifecycle and ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Navigation
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)
    
    // Kotlin Serialization
    implementation(libs.kotlinx.serialization.json)

    // Ktor for HTTP requests
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    
    // Gson for backup/restore
    implementation(libs.gson)
    
    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Biometric Authentication
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)
    implementation(libs.play.services.auth)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    
    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    
    // Hilt WorkManager integration
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    
    // LiteRT-LM for on-device LLM inference
    implementation(libs.litertlm.android)

    
    // Google Play In-App Updates (only for standard flavor)
    "standardImplementation"(libs.app.update)
    "standardImplementation"(libs.app.update.ktx)
    
    // Google Play In-App Reviews (only for standard flavor)
    "standardImplementation"(libs.review)
    "standardImplementation"(libs.review.ktx)
    
    testImplementation(libs.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
    // Robolectric (JVM) UI test stack — regression harness for navigation stability
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.espresso.core)
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    implementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    
    // Markdown support
    implementation(libs.markdown)
    implementation(libs.mikepenz.markdown.renderer)
    implementation(libs.mikepenz.markdown.renderer.m3)
    
    // OpenCSV for CSV export
    implementation(libs.opencsv)
    testImplementation(kotlin("test"))

    // coil for images and GIF
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.gif)

    // Compose Charts
    implementation(libs.compose.charts)

    // Reorderable
    implementation(libs.reorderable)

    // PDF Box for Android
    implementation(libs.pdfbox.android)
}
