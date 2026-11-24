plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.aidly.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aidly.app"
        minSdk = 26 // requerido por firebase-ai-ondevice
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0-hackathon"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    // Las pruebas unitarias usan clases que registran con android.util.Log: que devuelvan valores por defecto en vez de fallar.
    testOptions { unitTests.isReturnDefaultValues = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // El SDK on-device aun no esta en el BoM: fijar versiones a mano.
    // Deteccion de presencia en el telefono (Modo dock). Apache-2.0; los modelos van en assets/mediapipe.
    implementation("com.google.mediapipe:tasks-vision:1.2.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")   // el org.json de Android no funciona en las pruebas de JVM
    implementation("com.google.firebase:firebase-ai:18.2.0")
    implementation("com.google.firebase:firebase-ai-ondevice:16.0.0-beta08")
    // App Check (exigido en el proyecto). Debug para desarrollo; en produccion usar Play Integrity.
    implementation("com.google.firebase:firebase-appcheck-debug:20.1.0")
    // Circulo familiar: sesion anonima + un documento por codigo en Firestore.
    implementation(platform("com.google.firebase:firebase-bom:35.2.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    val camerax = "1.7.0"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    implementation("androidx.glance:glance-appwidget:1.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")

    implementation(platform("androidx.compose:compose-bom:2025.10.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
}
