import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 從 local.properties 讀取機密（該檔案已被 .gitignore 排除，永不進倉庫）
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.phantom.checker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.phantom.checker"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "1.6.0"

        buildConfigField("String", "MISTRAL_API_KEY", "\"${localProps.getProperty("mistral.apiKey", "")}\"")
        buildConfigField("String", "MISTRAL_BASE_URL", "\"${localProps.getProperty("mistral.baseUrl", "")}\"")
    }

    val ksFile = System.getenv("PHANTOM_KEYSTORE_FILE")
        ?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty("keystore.file")?.takeIf { it.isNotBlank() }
    val ksStorePassword = System.getenv("PHANTOM_KEYSTORE_PASSWORD")
        ?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty("keystore.storePassword")?.takeIf { it.isNotBlank() }
    val ksAlias = System.getenv("PHANTOM_KEY_ALIAS")
        ?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty("keystore.alias")?.takeIf { it.isNotBlank() }
    val ksKeyPassword = System.getenv("PHANTOM_KEY_PASSWORD")
        ?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty("keystore.keyPassword")?.takeIf { it.isNotBlank() }
    val hasReleaseSigning = ksFile != null &&
        ksStorePassword != null &&
        ksAlias != null &&
        ksKeyPassword != null &&
        file(ksFile).exists()

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(ksFile!!)
                storePassword = ksStorePassword
                keyAlias = ksAlias
                keyPassword = ksKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.ui:ui:1.7.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("androidx.security:security-crypto:1.1.0")

    implementation("androidx.work:work-runtime-ktx:2.9.1")

    implementation("dev.chrisbanes.haze:haze:1.5.4")
    implementation("dev.chrisbanes.haze:haze-materials:1.5.4")

    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}

configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.0.21")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.21")
    }
}
