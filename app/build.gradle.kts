plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "one.makenzi.iptv"
    compileSdk = 34

    defaultConfig {
        applicationId = "one.makenzi.iptv"
        minSdk = 23
        targetSdk = 34
        versionCode = 3
        versionName = "0.3.0"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            storeFile = file("keystore/makenzi-keystore.jks")
            storePassword = "1357246"
            keyAlias = "makenzi"
            keyPassword = "1357246"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    val media3 = "1.4.1" // можно 1.4.0, если 1.4.1 вдруг недоступна в момент билда

    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    // БЫЛО: implementation("androidx.media3:media3-exoplayer-rtmp:$media3")
    implementation("androidx.media3:media3-datasource-rtmp:$media3") // <-- ПРАВИЛЬНО
    ...
}
