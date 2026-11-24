import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

group = "com.phantom.checker.desktop"
version = "0.2.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("io.ktor:ktor-client-cio:3.6.0")
    implementation("io.ktor:ktor-client-websockets:3.6.0")
    implementation("org.jmdns:jmdns:3.6.4")
    implementation("com.google.code.gson:gson:2.15.0")
}

compose.desktop {
    application {
        mainClass = "com.phantom.checker.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "PhantomCheckDesktop"
            packageVersion = "0.2.0"
            description = "PhantomCheck 桌面端（區網加密連線與訊息管理）"
            vendor = "PhantomCheck"
            includeAllModules = true
            windows {
                iconFile.set(project.file("src/main/resources/icon.ico"))
                menuGroup = "PhantomCheck"
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "9B7E3C4A-6F1D-4E2A-9D8C-5A1B2C3D4E5F"
            }
        }
    }
}
