plugins {
    id("buildlogic.java-application-conventions")
    kotlin("plugin.serialization") version "2.4.10"
}

application {
    mainClass.set("org.key_project.key.api.doc.MainKt")
}

dependencies {
    implementation(project(":keyext.api"))
    implementation(libs.clickt)

    implementation("org.jetbrains:markdown:0.7.8")
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    // implementation("com.palantir.javapoet:javapoet:0.16.0")
}
