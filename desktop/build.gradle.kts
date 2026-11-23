plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

group = "com.phantom.checker.desktop"
version = "d0.1.0"

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
            packageName = "PhantomCheckDesktop"
            packageVersion = "1.0.0"
        }
    }
}
