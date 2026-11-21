plugins {
    id("com.android.application")
}

// API key comes from local.properties / -P / env, never from source control.
val presageApiKey: String = providers.gradleProperty("presageApiKey")
    .orElse(providers.environmentVariable("PRESAGE_API_KEY"))
    .orElse(
        providers.provider {
            val f = rootProject.file("local.properties")
            if (f.exists()) {
                java.util.Properties().apply { f.inputStream().use { load(it) } }
                    .getProperty("presageApiKey", "")
            } else ""
        },
    ).get()

android {
    namespace = "com.deanway.pulsemirror"
    compileSdk = 36
    compileSdkMinor = 1
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "com.deanway.pulsemirror"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "PRESAGE_API_KEY", "\"${presageApiKey.replace("\"", "")}\"")
    }

    buildFeatures { buildConfig = true }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.8.1")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.camera:camera-view:1.6.0")

    implementation("com.presagetech:smartspectra:3.5.0")
}
