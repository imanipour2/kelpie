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
val r5SourceQa = providers.gradleProperty("kelpieR5SourceQaProfile").orNull == "true"
val r5AgentQa = providers.gradleProperty("kelpieR5BackgroundAgentQaProfile").orNull == "true"
val r5PrivateBetaQa = providers.gradleProperty("kelpieR5PrivateBetaQaProfile").orNull == "true"
// Isolated upstream-connection compatibility probe; does not replace the R5 beta.
val r5MotherConnectionQa = providers.gradleProperty("kelpieR5MotherConnectionQaProfile").orNull == "true"
// Existing MotherNext package: updates require verified signer, recovery and owner gates.
val r5MotherNextQa = providers.gradleProperty("kelpieR5MotherNextQaProfile").orNull == "true"
val anyIsolatedQa = anyP4Qa || r5SourceQa || r5AgentQa || r5PrivateBetaQa || r5MotherConnectionQa || r5MotherNextQa
require(listOf(parallelP4Qa, existingP4Qa, r5SourceQa, r5AgentQa, r5PrivateBetaQa, r5MotherConnectionQa, r5MotherNextQa).count { it } <= 1) {
    "Isolated Android QA profiles are mutually exclusive"
}

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
        } else if (r5SourceQa) {
            versionCode = 31
            versionName = "0.2.11-r5-consent-qa"
        } else if (r5AgentQa) {
            versionCode = 33
            versionName = "0.2.13-r5-agent-qa"
        } else if (r5PrivateBetaQa) {
            versionCode = 37
            versionName = "0.2.17-r5-loopback-pair-qa"
        } else if (r5MotherConnectionQa) {
            versionCode = 39
            versionName = "0.2.19-r5-mother-background-qa"
        } else if (r5MotherNextQa) {
            versionCode = 41
            versionName = "0.2.21-r5-wave1-qa"
        }
        buildConfigField("int", "LOCAL_HTTP_PORT", "8420")
        buildConfigField("boolean", "PRIVATE_CAPTURE_QA_ENABLED", "false")
        buildConfigField("boolean", "R5_NATIVE_CONSENT_QA_ENABLED", "false")
        buildConfigField("boolean", "R5_BACKGROUND_AGENT_QA_ENABLED", "false")
        buildConfigField("boolean", "R5_MOTHER_MCP_BACKGROUND_QA_ENABLED", "false")
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    sourceSets {
        getByName("debug") {
            if (anyP4Qa) {
                manifest.srcFile("src/privateCaptureQa/AndroidManifest.xml")
            } else if (r5SourceQa || r5AgentQa || r5PrivateBetaQa || r5MotherConnectionQa || r5MotherNextQa) {
                manifest.srcFile("src/r5SourceQa/AndroidManifest.xml")
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
            } else if (r5SourceQa) {
                applicationIdSuffix = ".r5sourceverify"
                buildConfigField("int", "LOCAL_HTTP_PORT", "18457")
                buildConfigField("boolean", "PRIVATE_CAPTURE_QA_ENABLED", "false")
                buildConfigField("boolean", "R5_NATIVE_CONSENT_QA_ENABLED", "true")
            } else if (r5AgentQa) {
                applicationIdSuffix = ".r5fullverify"
                buildConfigField("int", "LOCAL_HTTP_PORT", "18460")
                buildConfigField("boolean", "PRIVATE_CAPTURE_QA_ENABLED", "false")
                buildConfigField("boolean", "R5_NATIVE_CONSENT_QA_ENABLED", "true")
                buildConfigField("boolean", "R5_BACKGROUND_AGENT_QA_ENABLED", "true")
            } else if (r5PrivateBetaQa) {
                // Separate owner-only beta identity: never wipe a working R5 login.
                applicationIdSuffix = ".r5betaverify"
                buildConfigField("int", "LOCAL_HTTP_PORT", "18461")
                buildConfigField("boolean", "PRIVATE_CAPTURE_QA_ENABLED", "false")
                buildConfigField("boolean", "R5_NATIVE_CONSENT_QA_ENABLED", "true")
                buildConfigField("boolean", "R5_BACKGROUND_AGENT_QA_ENABLED", "true")
            } else if (r5MotherConnectionQa) {
                // Owner-approved mother MCP + native R5 background rendering QA.
                // Keep standard /mcp, pairing and the existing port; no private MCP replacement.
                // The separate P4/R5 capture and agent branches remain intact.
                applicationIdSuffix = ".r5motherverify"
                buildConfigField("int", "LOCAL_HTTP_PORT", "18462")
                buildConfigField("boolean", "PRIVATE_CAPTURE_QA_ENABLED", "false")
                buildConfigField("boolean", "R5_NATIVE_CONSENT_QA_ENABLED", "true")
                buildConfigField("boolean", "R5_BACKGROUND_AGENT_QA_ENABLED", "true")
                buildConfigField("boolean", "R5_MOTHER_MCP_BACKGROUND_QA_ENABLED", "true")
            } else if (r5MotherNextQa) {
                // Preserve the installed identity and port; no parallel-install fallback.
                applicationIdSuffix = ".r5mothernextverify"
                buildConfigField("int", "LOCAL_HTTP_PORT", "18463")
                buildConfigField("boolean", "PRIVATE_CAPTURE_QA_ENABLED", "false")
                buildConfigField("boolean", "R5_NATIVE_CONSENT_QA_ENABLED", "true")
                buildConfigField("boolean", "R5_BACKGROUND_AGENT_QA_ENABLED", "true")
                buildConfigField("boolean", "R5_MOTHER_MCP_BACKGROUND_QA_ENABLED", "true")
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
            if (anyIsolatedQa) {
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
