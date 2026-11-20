import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.kiloquest"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.kiloquest"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // Server-Zugang kommt aus local.properties (nicht im Repo): valhalla.url / valhalla.token
        val local = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        buildConfigField("String", "VALHALLA_URL", "\"${local.getProperty("valhalla.url", "")}\"")
        buildConfigField("String", "VALHALLA_TOKEN", "\"${local.getProperty("valhalla.token", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions { jvmTarget = "21" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions { unitTests { isIncludeAndroidResources = true } }
    lint {
        // i18n von Tag 1: harte Strings sind Fehler
        error += "HardcodedText"
    }
}

dependencies {
    implementation(project(":game-engine"))
    implementation(project(":nav-core"))
    implementation(project(":link-core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.maplibre)
    implementation(libs.androidx.car.app)
    implementation(libs.androidx.work) // Apache-2.0: Erinnerungen vor Reisebeginn
    implementation(libs.androidx.camera.core) // Apache-2.0: Roadtrip-Fotostory (beide Kameras)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    // Layout-Pruefung ohne Geraet: Compose-Screenshots in Robolectric (nur Test, siehe app/src/test)
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation(platform(libs.compose.bom))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
