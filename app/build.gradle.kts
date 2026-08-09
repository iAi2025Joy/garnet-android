
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // Change this to your own reverse-domain package name before
    // publishing -- org.instituteofai.garnet is a reasonable default
    // matching the site's branding, but Google Play requires a package
    // name unique to your developer account.
    namespace = "org.instituteofai.garnet"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.instituteofai.garnet"
        minSdk = 24 // Android 7.0+ -- covers the vast majority of active devices while keeping modern WebView/media APIs available
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.google.android.material:material:1.12.0")
}

