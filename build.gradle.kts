import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.25"
    id("org.jlleitschuh.gradle.ktlint") version "12.3.0" // Use latest
}

allprojects {
    group = "dev.botak"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven("https://repo.gradle.org/gradle/libs-releases/")
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    dependencies {
        testImplementation(kotlin("test"))

        implementation(kotlin("stdlib"))
        implementation("com.typesafe:config:1.4.3")
        implementation("org.slf4j:slf4j-api:2.0.12")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
        implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")
        runtimeOnly("ch.qos.logback:logback-classic:1.4.11")

        implementation("com.fasterxml.jackson.core:jackson-databind:2.19.1")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.19.0")
        runtimeOnly("com.fasterxml.jackson.module:jackson-module-kotlin:2.19.0")
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
        javaParameters = true
    }
}
