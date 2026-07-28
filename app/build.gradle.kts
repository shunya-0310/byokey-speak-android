plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val aiReportEndpoint = providers.gradleProperty("BYOKEY_AI_REPORT_ENDPOINT")
    .orElse(providers.environmentVariable("BYOKEY_AI_REPORT_ENDPOINT"))
    .orElse("")
val releaseStoreFile = providers.gradleProperty("BYOKEY_RELEASE_STORE_FILE")
    .orElse(providers.environmentVariable("BYOKEY_RELEASE_STORE_FILE"))
val releaseStorePassword = providers.gradleProperty("BYOKEY_RELEASE_STORE_PASSWORD")
    .orElse(providers.environmentVariable("BYOKEY_RELEASE_STORE_PASSWORD"))
val releaseKeyAlias = providers.gradleProperty("BYOKEY_RELEASE_KEY_ALIAS")
    .orElse(providers.environmentVariable("BYOKEY_RELEASE_KEY_ALIAS"))
val releaseKeyPassword = providers.gradleProperty("BYOKEY_RELEASE_KEY_PASSWORD")
    .orElse(providers.environmentVariable("BYOKEY_RELEASE_KEY_PASSWORD"))
val releaseSigningConfigured = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { it.isPresent }

android {
    namespace = "com.byokeylab.speak"
    buildToolsVersion = "36.1.0"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.byokeylab.speak"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField(
            "String",
            "AI_REPORT_ENDPOINT",
            "\"${aiReportEndpoint.get().replace("\\", "\\\\").replace("\"", "\\\"")}\""
        )
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseStoreFile.get())
                storePassword = releaseStorePassword.get()
                keyAlias = releaseKeyAlias.get()
                keyPassword = releaseKeyPassword.get()
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
            isShrinkResources = true
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.room:room-runtime:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
