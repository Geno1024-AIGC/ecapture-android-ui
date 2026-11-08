import com.geno1024.aigc.ecaptureui.gradle.Versioning
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

val stamp = Versioning.stamp(rootDir, projectDir)

// ---- Signing: org shared key, env (CI) or local.properties. Never hardcode. ----
val keystoreProps = Properties().apply {
    val local = File(rootProject.projectDir, "local.properties")
    if (local.exists()) local.inputStream().use { load(it) }
}
fun secret(key: String, env: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() }
        ?: keystoreProps.getProperty(key)?.takeIf { it.isNotBlank() }
val releaseStoreFile = secret("keystore.storeFile", "KEYSTORE_STORE_FILE")
val releaseStorePassword = secret("keystore.storePassword", "KEYSTORE_STORE_PASSWORD")
val releaseKeyPassword = secret("keystore.keyPassword", "KEYSTORE_KEY_PASSWORD") ?: releaseStorePassword
val releaseKeyAlias = secret("keystore.keyAlias", "KEYSTORE_KEY_ALIAS") ?: "geno"

android {
    namespace = "com.geno1024.aigc.ecaptureui"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.geno1024.aigc.ecaptureui"
        minSdk = 26
        targetSdk = 37
        versionCode = stamp.versionCode
        versionName = stamp.versionName
        buildConfigField("String", "GIT_SHA", "\"${stamp.sha1}\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        if (releaseStoreFile != null && releaseStorePassword != null) {
            create("release") {
                storeFile = File(releaseStoreFile)
                storePassword = releaseStorePassword
                keyPassword = releaseKeyPassword
                keyAlias = releaseKeyAlias
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            if (releaseStoreFile != null && releaseStorePassword != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseStoreFile != null && releaseStorePassword != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/versions/**",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/INDEX.LIST",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)
}

Versioning.bumpOnPackaging(project, projectDir)
