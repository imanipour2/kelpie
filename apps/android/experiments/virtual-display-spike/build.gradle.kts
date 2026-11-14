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
    namespace = "com.kelpie.virtualdisplayprobe"
    compileSdk = 34

    defaultConfig {
        // Separate application ID: never replaces the browser installed on S24.
        applicationId = "com.kelpie.virtualdisplayprobe.p1"
        minSdk = 28
        targetSdk = 34
        versionCode = 3
        versionName = "0.2.1-p1-qa"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
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
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
