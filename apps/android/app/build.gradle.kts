plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jlleitschuh.gradle.ktlint")
}

ktlint {
    version.set("1.5.0")
    android.set(true)
    verbose.set(true)
    outputToConsole.set(true)
}

// A second, isolated P4-only QA identity; never replace pcverify or unifiedverify.
val parallelP4Qa = providers.gradleProperty("kelpieParallelCaptureQaProfile").orNull == "true"
val existingP4Qa = providers.gradleProperty("kelpiePrivateCaptureQaProfile").orNull == "true"
val anyP4Qa = parallelP4Qa || existingP4Qa

android {
    namespace = "com.unlikeotherai.kelpie.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kelpie.browser"
        minSdk = 28
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 25
        versionName = "0.2.5"
        if (existingP4Qa) {
            versionCode = 29
            versionName = "0.2.9-qa"
        } else if (parallelP4Qa) {
            versionCode = 30
            versionName = "0.2.10-parallel-qa"
        }
        buildConfigField("int", "LOCAL_HTTP_PORT", "8420")
        buildConfigField("boolean", "PRIVATE_CAPTURE_QA_ENABLED", "false")
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    sourceSets {
        getByName("debug") {
            if (anyP4Qa) {
                manifest.srcFile("src/privateCaptureQa/AndroidManifest.xml")
            }
        }
    }

    buildTypes {
        debug {
            if (providers.gradleProperty("kelpieEmbeddingVerificationProfile").orNull == "true") {
                applicationIdSuffix = ".embeddingverify"
                buildConfigField("int", "LOCAL_HTTP_PORT", "18431")
            } else if (providers.gradleProperty("kelpieVerificationProfile").orNull == "true") {
                applicationIdSuffix = ".nessieverify"
                buildConfigField("int", "LOCAL_HTTP_PORT", "18420")
            } else if (parallelP4Qa) {
                applicationIdSuffix = ".pcparallelverify"
                buildConfigField("int", "LOCAL_HTTP_PORT", "18447")
                buildConfigField("boolean", "PRIVATE_CAPTURE_QA_ENABLED", "true")
            } else if (existingP4Qa) {
                applicationIdSuffix = ".pcverify"
                buildConfigField("int", "LOCAL_HTTP_PORT", "18437")
                buildConfigField("boolean", "PRIVATE_CAPTURE_QA_ENABLED", "true")
            }
        }
        release {
            isMinifyEnabled = false
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    packaging {
        jniLibs {
            // AGP 8.3 does not reliably emit 16 KiB-aligned uncompressed
            // JNI entries. Extract compressed libs in the isolated QA APK
            // until a separately reviewed toolchain upgrade.
            if (anyP4Qa) {
                useLegacyPackaging = true
            }
        }
        resources {
            excludes +=
                setOf(
                    "META-INF/INDEX.LIST",
                    "META-INF/io.netty.versions.properties",
                    "META-INF/DEPENDENCIES",
                    "META-INF/LICENSE.md",
                    "META-INF/NOTICE.md",
                )
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    implementation(project(":kelpie-browser"))
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
