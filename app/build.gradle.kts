plugins {
    id("com.android.application")
}

android {
    namespace = "pl.kalkulator.grzbiet"
    compileSdk = 34

    defaultConfig {
        applicationId = "pl.kalkulator.grzbiet"
        minSdk = 26
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    signingConfigs {
        create("own") {
            storeFile = file("keystore/kalkulator.jks")
            storePassword = "kalkulator123"
            keyAlias = "kalkulator"
            keyPassword = "kalkulator123"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("own")
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
