plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing material comes from the environment (injected by Jenkins) or -P
// Gradle properties, never from a file in the repo. Absent → release APK is unsigned.
fun secret(name: String): String? =
    System.getenv(name) ?: providers.gradleProperty(name).orNull

val releaseStoreFile: String? = secret("RELEASE_STORE_FILE")

android {
    namespace = "com.gowthaman.callbkp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gowthaman.callbkp"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            if (releaseStoreFile != null) {
                storeFile = file(releaseStoreFile)
                storePassword = secret("RELEASE_STORE_PASSWORD")
                keyAlias = secret("RELEASE_KEY_ALIAS")
                keyPassword = secret("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // No runtime dependencies: plain android.app.Activity + the framework's org.json.
    testImplementation("junit:junit:4.13.2")
    // The framework's org.json is a stub in local unit tests; use the real one there.
    testImplementation("org.json:json:20240303")
}
