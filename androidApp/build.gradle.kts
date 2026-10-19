import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "cg.creamgod.boarderless"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "cg.creamgod.boarderless"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionCode =
            providers
                .gradleProperty("appVersionCode")
                .orElse("1")
                .get()
                .toInt()
        versionName = providers.gradleProperty("appVersion").orElse("1.0.0").get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    val keystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
    if (!keystorePath.isNullOrBlank()) {
        val releaseStorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
            ?.takeIf { it.isNotEmpty() }
            ?: throw GradleException("ANDROID_KEYSTORE_PASSWORD is required when signing Android releases")
        val releaseKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
            ?.takeIf { it.isNotBlank() }
            ?: throw GradleException("ANDROID_KEY_ALIAS is required when signing Android releases")
        // GitHub exposes an unset Secret as an empty string, so orElse alone is insufficient.
        val releaseKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
            ?.takeIf { it.isNotEmpty() }
            ?: releaseStorePassword
        signingConfigs.create("release") {
            storeFile = file(keystorePath)
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (!keystorePath.isNullOrBlank()) signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}
