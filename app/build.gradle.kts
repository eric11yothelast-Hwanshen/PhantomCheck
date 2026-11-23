import java.util.Properties

plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 從 local.properties 讀取機密（該檔案已被 .gitignore 排除，永不進倉庫）
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.phantom.checker"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.phantom.checker"
        minSdk = 26
        targetSdk = 36
        versionCode = 51
        versionName = "4.3.0"

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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")

    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("androidx.security:security-crypto:1.1.0")

    implementation("androidx.work:work-runtime-ktx:2.12.0")

    implementation("dev.chrisbanes.haze:haze:2.1.0")
    implementation("dev.chrisbanes.haze:haze-blur:2.1.0")

    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    implementation("androidx.health.connect:connect-client:1.1.0")

    // #449 桌面端區網連線（M1）
    implementation(project(":shared"))
    implementation("io.ktor:ktor-server-core:3.6.0")
    implementation("io.ktor:ktor-server-cio:3.6.0")
    implementation("io.ktor:ktor-server-websockets:3.6.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.87")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.87")
    implementation("io.github.g0dkar:qrcode-kotlin:4.5.0")
}

configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:2.4.21")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.4.21")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.4.21")
    }
}
