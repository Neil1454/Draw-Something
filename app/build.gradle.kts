import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.neil1454.drawsomething"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.neil1454.drawsomething"
        minSdk = 26
        targetSdk = 35
        versionCode = 26
        versionName = "2.9.0"
    }

    // A fixed key, so a new version installs over the old one instead of needing an uninstall.
    signingConfigs {
        // The convenience key committed to the repo. It is NOT secret (anyone can read it), so it is
        // only the fallback; the real release key comes from the RELEASE_* settings below.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // The release key. When the build robot has a private key stored as GitHub secrets it passes
        // them in as RELEASE_KEYSTORE_FILE / _PASSWORD / KEY_ALIAS / KEY_PASSWORD. Without them we fall
        // back to the committed key so the project still builds anywhere.
        create("release") {
            val keystore = System.getenv("RELEASE_KEYSTORE_FILE")
            if (keystore != null && File(keystore).exists()) {
                storeFile = File(keystore)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            } else {
                storeFile = file("debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false // a released app must not be inspectable with developer tools
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
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    // The Nearby library drags in a very old Fragment; pin a current one so permission requests behave.
    implementation("androidx.fragment:fragment:1.8.5")

    // Offline phone-to-phone play over Bluetooth + Wi-Fi (no internet needed).
    implementation("com.google.android.gms:play-services-nearby:19.3.0")

    testImplementation("junit:junit:4.13.2")
    // Android's org.json is only a stub in local unit tests; use the real one.
    testImplementation("org.json:json:20240303")
}
