plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.dalim.datalimit"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dalim.datalimit"
        minSdk = 26
        targetSdk = 34
        versionCode = 6
        versionName = "0.3.1"
    }

    signingConfigs {
        create("release") {
            val ksPath = System.getenv("DALIM_KEYSTORE_FILE")
            if (!ksPath.isNullOrBlank()) {
                storeFile = file(ksPath)
                storePassword = System.getenv("DALIM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("DALIM_KEY_ALIAS")
                keyPassword = System.getenv("DALIM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (System.getenv("DALIM_KEYSTORE_FILE").isNullOrBlank()) null
                else signingConfigs.getByName("release")
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}