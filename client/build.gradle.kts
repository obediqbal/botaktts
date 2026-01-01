import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.compose") version "1.8.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0"
}

val isDev: Provider<Boolean> =
    providers.gradleProperty("dev").map { it.equals("true", ignoreCase = true) }.orElse(false)

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    implementation(project(":core"))

// https://mvnrepository.com/artifact/com.1stleg/jnativehook
    implementation("com.1stleg:jnativehook:2.1.0")
    implementation("net.java.dev.jna:jna:5.13.0")
    implementation("net.java.dev.jna:jna-platform:5.13.0")
    implementation(compose.desktop.currentOs)
    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "dev.botak.client.MainKt"
        if (isDev.get()) {
            jvmArgs += listOf("-DisDev=true")
        }
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "BotakTTSClient"
            packageVersion = project.version as String?

            windows {
                upgradeUuid = "fb8d6aa0-dab3-4864-be2f-d14aafec4818"
                msiPackageVersion = project.version as String?
                console = false
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}

tasks.register("printVersion") {
    doLast {
        println(project.version)
    }
}

// NSIS distribution configurations
val appImageDir = layout.buildDirectory.dir("compose/binaries/main/app/BotakTTSClient")
val distDir = layout.buildDirectory.dir("distributions")

// Create distributions directory
tasks.register("prepareDistributions") {
    dependsOn("createDistributable")
    doLast {
        distDir.get().asFile.mkdirs()
    }
}

// Create installer executable task
tasks.register("createInstaller") {
    dependsOn("prepareDistributions")
    doLast {
        // Run NSIS with installer script
        exec {
            workingDir = file("${rootProject.projectDir}/scripts")
            commandLine(
                "makensis",
                "/DVERSION=${project.version}",
                "/DAPP_DIR=${appImageDir.get().asFile.absolutePath}",
                "installer.nsi",
            )
        }
        // Move the generated installer to distributions folder
        val installerFile = file("${rootProject.projectDir}/scripts/BotakTTS-${project.version}-Setup.exe")
        if (installerFile.exists()) {
            installerFile.copyTo(distDir.get().file("BotakTTS-${project.version}-Setup.exe").asFile, overwrite = true)
            installerFile.delete()
        }
    }
}

// Create portable executable task
tasks.register("createPortable") {
    dependsOn("prepareDistributions")
    doLast {
        // Run NSIS with portable script
        exec {
            workingDir = file("${rootProject.projectDir}/scripts")
            commandLine(
                "makensis",
                "/DVERSION=${project.version}",
                "/DAPP_DIR=${appImageDir.get().asFile.absolutePath}",
                "portable.nsi",
            )
        }
        // Move the generated portable to distributions folder
        val portableFile = file("${rootProject.projectDir}/scripts/BotakTTS-${project.version}-Portable.exe")
        if (portableFile.exists()) {
            portableFile.copyTo(distDir.get().file("BotakTTS-${project.version}-Portable.exe").asFile, overwrite = true)
            portableFile.delete()
        }
    }
}
