import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// AGP 9 compiles Kotlin natively (built-in Kotlin); only the Compose compiler plugin is applied
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appVersionName = "1.0.2"

// Release signing: keystore.properties (git-ignored) or OTAGHOME_* environment variables.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? = keystoreProperties.getProperty(key) ?: System.getenv(env)
val releaseStoreFile = signingValue("storeFile", "OTAGHOME_KEYSTORE_FILE")

android {
    namespace = "com.berkbb.otaghome"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "com.berkbb.otaghome"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = appVersionName
    }

    signingConfigs {
        create("release") {
            if (releaseStoreFile != null) {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingValue("storePassword", "OTAGHOME_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "OTAGHOME_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "OTAGHOME_KEY_PASSWORD")
            }
        }
    }

    testBuildType = "release"

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (releaseStoreFile != null) {
                signingConfigs.getByName("release")
            } else {
                // Same fallback as the other TV apps: this Mac's debug key (see release-cert.sha256)
                logger.warn("OtagHome: no release keystore configured (keystore.properties / OTAGHOME_KEYSTORE_FILE); signing release with the debug key.")
                signingConfigs.getByName("debug")
            }
        }
    }

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
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Pure Kotlin; the only native code is Compose's ~10 KB androidx.graphics.path per ABI, so one universal APK serves every ABI (named like the other TV apps)
base {
    archivesName.set("otaghome-v$appVersionName-universal")
}

androidComponents {
    // Release-only packaging: the debug variant is disabled
    beforeVariants(selector().withBuildType("debug")) { variantBuilder ->
        variantBuilder.enable = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    testImplementation("junit:junit:4.13.2")
    // Android's org.json is a stub in local unit tests; the reference implementation stands in for it
    testImplementation("org.json:json:20240303")
}
