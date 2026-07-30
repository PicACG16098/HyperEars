plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.hyperears"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.hyperears"
        minSdk = 35
        targetSdk = 36
        versionCode = 14
        versionName = "0.6.1"
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            // Preserve the certificate used by the currently enabled test deployment so
            // Release can replace it without uninstalling the LSPosed module.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"

    lint {
        // Android 16 is the deliberate deployment target for the current HyperOS device.
        disable += "OldTargetApi"
        warningsAsErrors = true
    }
}

dependencies {
    implementation(project(":integration"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    compileOnly(libs.libxposed.api)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
