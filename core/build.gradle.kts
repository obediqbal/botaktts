plugins {
    application
}

dependencies {
    implementation(platform("com.google.cloud:libraries-bom:26.66.0"))
    implementation("com.google.cloud:google-cloud-texttospeech")
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("dev.botak.core.MainKt")
}
