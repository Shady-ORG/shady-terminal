import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

group = "cli.shady"
version = "1.0.0"

repositories {
    google()
    mavenCentral()
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.pty4j)
    implementation(libs.jediterm.ui)
    implementation(libs.jediterm.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.compose.ui.test.junit4)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.junit.vintage.engine)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "cli.shady.MainKt"
        attributes["Implementation-Version"] = project.version.toString()
    }
}

tasks.test {
    useJUnitPlatform()
    systemProperty("user.home", layout.buildDirectory.dir("test-home").get().asFile.absolutePath)
}

compose.desktop {
    application {
        mainClass = "cli.shady.MainKt"
        args("start")
        jvmArgs("--enable-native-access=ALL-UNNAMED")

        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "Shady"
            packageVersion = project.version.toString()
            description = "Interactive desktop terminal emulator"
            vendor = "Shady"

            macOS {
                bundleID = "cli.shady.app"
                iconFile.set(project.file("src/main/resources/shady-icon.icns"))
            }

            includeAllModules = true
        }
    }
}

val installDist by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Installs shady into build/install/shady for local use."
    val jarTask = tasks.jar
    val runtimeClasspath = configurations.runtimeClasspath
    dependsOn(jarTask)
    into(layout.buildDirectory.dir("install/shady"))
    into("lib") {
        from(jarTask)
        from(runtimeClasspath)
    }
    into("scripts") {
        from("scripts")
    }
    doLast {
        val binDir = layout.buildDirectory.dir("install/shady/bin").get().asFile
        binDir.mkdirs()
        val launcher = File(binDir, "shady")
        val jvmArgs = "--enable-native-access=ALL-UNNAMED"
        launcher.writeText(
            """
            |#!/bin/sh
            |APP_HOME="${'$'}{0%/*}/.."
            |exec java $jvmArgs -cp "${'$'}APP_HOME/lib/*" cli.shady.MainKt "${'$'}@"
            """.trimMargin() + "\n",
        )
        launcher.setExecutable(true)
    }
}

tasks.register("buildDMG") {
    group = "distribution"
    description = "Builds an unsigned macOS DMG with bundled Java runtime."
    dependsOn("packageDmg")
}
