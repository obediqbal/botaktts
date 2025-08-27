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
                console = true
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
