plugins {
    id("buildlogic.java-library-conventions")
    application
    alias(libs.plugins.shadow)
    kotlin("kapt")
}

application {
    mainClass = "org.key_project.key.lsp.Main"
}

dependencies {
    api(project(":keyext.api"))
    api(libs.lsp4j.jsonrpc)
    api(libs.lsp4j.lsp)
    implementation(libs.lsp4j.websocket.jakarta)
    implementation(libs.jetty.websocket.javax.server)
    implementation(libs.picocli)
    implementation(libs.guava)
    implementation(libs.auto.service)
    implementation(libs.clickt)

    annotationProcessor(libs.therapi.runtime.javadoc.scribe)
    api(libs.therapi.runtime.javadoc)

    implementation("io.github.wadoon:kotlin-prettyprinting:1.1.0")

    testImplementation(libs.assertj.core)
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation(kotlin("test"))
}

tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.add("-parameters") // for having parameter name in reflection
}

repositories {
    mavenCentral()
}