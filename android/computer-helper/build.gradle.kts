// Experimental auxiliary package for the original Tama V2 computer (option 1 proof of concept).
// Own applicationId and UID, no INTERNET permission, fixed operations only. Not a product release.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.novoagentehb.computerhelper"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.novoagentehb.computerhelper"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.0.1-poc"
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        named("main") {
            java.setSrcDirs(listOf("src/main/kotlin", "../computer-contract/src/main/kotlin"))
        }
    }
}
