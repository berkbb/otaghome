import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// AGP 9 compiles Kotlin natively (built-in Kotlin); only the Compose compiler plugin is applied
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appVersionName = "1.0.3"

// Release signing, first match wins: keystore.properties (git-ignored), OTAGHOME_* environment
// variables, or the TV apps' shared keystore ~/.signing/babadogan-tv-release.p12, whose password
// is the macOS Keychain item "babadogan-tv-release-keystore".
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? = keystoreProperties.getProperty(key) ?: System.getenv(env)
val sharedKeystore = File(System.getProperty("user.home"), ".signing/babadogan-tv-release.p12")
val releaseStoreFile = signingValue("storeFile", "OTAGHOME_KEYSTORE_FILE") ?: sharedKeystore.takeIf { it.isFile }?.path
// macOS only; elsewhere the password comes from keystore.properties or the environment.
fun keychainPassword(): String? = runCatching {
    providers.exec {
        commandLine("security", "find-generic-password", "-s", "babadogan-tv-release-keystore", "-w")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
}.getOrNull()?.ifEmpty { null }
val releaseStorePassword = releaseStoreFile?.let { signingValue("storePassword", "OTAGHOME_KEYSTORE_PASSWORD") ?: keychainPassword() }

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
        versionCode = 4
        versionName = appVersionName
    }

    signingConfigs {
        create("release") {
            if (releaseStoreFile != null) {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = signingValue("keyAlias", "OTAGHOME_KEY_ALIAS") ?: "babadogan-tv"
                keyPassword = signingValue("keyPassword", "OTAGHOME_KEY_PASSWORD") ?: releaseStorePassword
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
                // Same fallback as the other TV apps; debug-signed APKs cannot update installed TVs
                logger.warn("OtagHome: no release keystore (keystore.properties, OTAGHOME_KEYSTORE_FILE or ~/.signing/babadogan-tv-release.p12); signing release with the debug key.")
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
