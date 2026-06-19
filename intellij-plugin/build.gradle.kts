import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.21"
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

group = "cli.shady"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        val localIdePath = providers.gradleProperty("localIdePath").orNull
        if (localIdePath != null) {
            local(localIdePath)
        } else {
            intellijIdea("2025.3.4")
        }
        bundledPlugin("org.jetbrains.plugins.terminal")
    }
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

intellijPlatform {
    pluginConfiguration {
        name = "Shady Terminal"
        version = project.version.toString()
        description = "Opens the installed Shady shell inside the IntelliJ terminal tool window."
        ideaVersion {
            sinceBuild = "253"
            untilBuild = provider { null }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
