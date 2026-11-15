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

android {
    namespace = "com.unlikeotherai.kelpie.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kelpie.browser"
        minSdk = 28
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 27
        versionName = "0.2.7-qa"
        buildConfigField("int", "LOCAL_HTTP_PORT", "8420")
        buildConfigField("boolean", "P2_QA_ENABLED", "false")
        buildConfigField("boolean", "P3_QA_ENABLED", "false")
        manifestPlaceholders["p2QaEnabled"] = "false"
        manifestPlaceholders["p3QaEnabled"] = "false"
        manifestPlaceholders["p2AppLabel"] = "Kelpie"
        manifestPlaceholders["p2NormalBrowserEnabled"] = "true"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    // Only the explicitly opted-in isolated P3 QA variant removes inherited
    // browser permissions. Standard, P2 and unified builds use their own manifest.
    sourceSets {
        getByName("debug") {
            if (providers.gradleProperty("kelpieP3VerificationProfile").orNull == "true") {
                manifest.srcFile("src/p3Qa/AndroidManifest.xml")
            }
        }
    }

    buildTypes {
        debug {
            if (providers.gradleProperty("kelpieP3VerificationProfile").orNull == "true") {
                applicationIdSuffix = ".p3verify"
                buildConfigField("int", "LOCAL_HTTP_PORT", "18451")
                buildConfigField("boolean", "P3_QA_ENABLED", "true")
                manifestPlaceholders["p3QaEnabled"] = "true"
                manifestPlaceholders["p2AppLabel"] = "Kelpie P3 Compose QA"
                manifestPlaceholders["p2NormalBrowserEnabled"] = "false"
            } else if (providers.gradleProperty("kelpieP2VerificationProfile").orNull == "true") {
                applicationIdSuffix = ".p2verify"
                buildConfigField("int", "LOCAL_HTTP_PORT", "18450")
                buildConfigField("boolean", "P2_QA_ENABLED", "true")
                manifestPlaceholders["p2QaEnabled"] = "true"
                manifestPlaceholders["p2AppLabel"] = "Kelpie P2 TabStore QA"
                manifestPlaceholders["p2NormalBrowserEnabled"] = "false"
            } else if (providers.gradleProperty("kelpieEmbeddingVerificationProfile").orNull == "true") {
                applicationIdSuffix = ".embeddingverify"
                buildConfigField("int", "LOCAL_HTTP_PORT", "18431")
            } else if (providers.gradleProperty("kelpieUnifiedVerificationProfile").orNull == "true") {
                applicationIdSuffix = ".unifiedverify"
                buildConfigField("int", "LOCAL_HTTP_PORT", "18440")
            } else if (providers.gradleProperty("kelpieVerificationProfile").orNull == "true") {
                applicationIdSuffix = ".nessieverify"
                buildConfigField("int", "LOCAL_HTTP_PORT", "18420")
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
    // P3 QA host composes the actual core TabWebViewContent; expose UI modules to the host.
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    implementation(project(":kelpie-browser"))
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
