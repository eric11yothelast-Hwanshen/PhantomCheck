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
        versionCode = 63
        versionName = "5.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

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
        debug {
            buildConfigField("String", "MISTRAL_API_KEY", "\"${localProps.getProperty("mistral.apiKey", "")}\"")
        }
        release {
            // Release 建置一律禁止注入開發機 local.properties 之 API key
            buildConfigField("String", "MISTRAL_API_KEY", "\"\"")
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

    gradle.taskGraph.whenReady {
        val requiresReleaseSigning = allTasks.any { task ->
            task.project == project && task.name in setOf("assembleRelease", "bundleRelease", "packageRelease")
        }
        if (requiresReleaseSigning && !hasReleaseSigning) {
            throw GradleException(
                "Release signing config is missing. Provide PHANTOM_KEYSTORE_FILE, PHANTOM_KEYSTORE_PASSWORD, " +
                    "PHANTOM_KEY_ALIAS, and PHANTOM_KEY_PASSWORD (or keystore.* in local.properties) before running assembleRelease."
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging {
        resources {
            excludes += listOf(
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
                "META-INF/NOTICE.md",
                "META-INF/license/**",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            )
        }
    }
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

    // #476 桌面端區網連線協定 DTO（手機端改用既有 OkHttp WebSocket 用戶端，移除 Ktor/Netty/BC/qrcode-kotlin）
    implementation(project(":shared"))

    // #477 手機掃碼配對：ML Kit bundled（內建模型、離線可用、無 GMS 依賴）＋ CameraX 1.6.2
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    val cameraxVersion = "1.6.2"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // #585 E2E/UI 測試基礎建設：僅新增 androidTest 依賴，不動既有版本／BOM／簽章／R8 設定。
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:2.4.21")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.4.21")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.4.21")
    }
}
