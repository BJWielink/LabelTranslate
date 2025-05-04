import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "org.label.translate"
version = "1.9-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("com.google.code.gson:gson:2.8.9")

    intellijPlatform {
        create("PS", "2025.1.0.1")
        plugins(listOf(/* Plugin Dependencies */))
    }
}

intellijPlatform {
    pluginVerification {
        ides {
            ide(IntelliJPlatformType.PhpStorm, "2025.1.0.1")
        }
    }
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "222"
            untilBuild = provider { null }
        }
    }
    publishing {
        token = System.getenv("PUBLISH_TOKEN")
    }
    signing {
        certificateChain = System.getenv("CERTIFICATE_CHAIN")
        privateKey = System.getenv("PRIVATE_KEY")
        password = System.getenv("PRIVATE_KEY_PASSWORD")
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
}
