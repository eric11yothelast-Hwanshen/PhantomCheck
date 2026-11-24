import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

val desktopVersion = "0.3.0"

group = "com.phantom.checker.desktop"
version = desktopVersion

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
    implementation("io.ktor:ktor-server-core:3.6.0")
    implementation("io.ktor:ktor-server-netty:3.6.0")
    implementation("io.ktor:ktor-server-websockets:3.6.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.87")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.87")
    implementation("org.jmdns:jmdns:3.6.5")
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.google.code.gson:gson:2.15.0")
}

compose.desktop {
    application {
        mainClass = "com.phantom.checker.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "PhantomCheckDesktop"
            packageVersion = desktopVersion
            description = "PhantomCheck Desktop - Encrypted LAN Link & Message Manager"
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
