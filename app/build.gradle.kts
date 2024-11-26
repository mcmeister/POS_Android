plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.devtools.ksp") version "1.9.0-1.0.13"
}

android {
    namespace = "com.example.pos"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.pos"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Signing configuration for release build
    signingConfigs {
        create("release") {
            keyAlias = "AliasPos" // Key alias as a string
            keyPassword = "online" // Key password
            storeFile = file("/home/mcmeister/Documents/POS_App_Dev/keystore_pos.jks") // Keystore path
            storePassword = "online" // Store password
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release") // Corrected the signing config
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }

    packaging {
        resources.excludes.add("/META-INF/DEPENDENCIES")
        resources.excludes.add("/META-INF/LICENSE")
        resources.excludes.add("/META-INF/LICENSE.txt")
        resources.excludes.add("/META-INF/NOTICE")
        resources.excludes.add("/META-INF/NOTICE.txt")
        resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    implementation("androidx.multidex:multidex:2.0.1")
    // Excel
    implementation("org.apache.poi:poi:5.2.3")
    implementation("org.apache.poi:poi-ooxml:5.2.3")

    // Google Sheets API
    implementation("com.google.apis:google-api-services-sheets:v4-rev612-1.25.0")

    // Google Play Services - Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Google API Client for Drive
    implementation("com.google.apis:google-api-services-drive:v3-rev197-1.25.0")

    // Google HTTP Client and Jackson
    implementation("com.google.http-client:google-http-client-jackson2:1.42.0")
    implementation("com.google.api-client:google-api-client-android:1.32.1")
    implementation("com.google.api-client:google-api-client-gson:1.32.1")
    implementation("com.google.http-client:google-http-client-android:1.40.1")

    // Optional: For working with HTTP on Android
    implementation("com.google.http-client:google-http-client-gson:1.42.0")

    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.9.0")

    // AndroidX and other libraries
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("com.github.bumptech.glide:glide:4.12.0")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.appcompat:appcompat:1.4.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.google.android.material:material:1.5.0")
    implementation("androidx.fragment:fragment-ktx:1.5.5")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    implementation("androidx.core:core-ktx:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.5.0")
    implementation("androidx.activity:activity-compose:1.5.0")
    implementation(platform("androidx.compose:compose-bom:2023.03.00"))
    implementation("androidx.compose.ui:ui:1.7.5")
    implementation("androidx.compose.ui:ui-graphics:1.7.5")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.5")
    implementation("androidx.compose.material3:material3:1.3.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.03.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.7.5")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.5")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.5")
}

apply(plugin = "com.google.gms.google-services")
