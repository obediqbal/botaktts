plugins {
    application
}

dependencies {
    implementation(platform("com.google.cloud:libraries-bom:26.66.0"))
    implementation("com.google.cloud:google-cloud-texttospeech")
    // The BOM will manage the module versions and transitive dependencies
    implementation(platform("com.google.auth:google-auth-library-bom:1.30.1"))
    // Replace with the module(s) that are needed
    implementation("com.google.auth:google-auth-library-oauth2-http")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("dev.botak.core.MainKt")
}
