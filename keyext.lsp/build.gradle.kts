plugins {
    id("buildlogic.java-library-conventions")
    application
    alias(libs.plugins.shadow)
    kotlin("kapt")
}

dependencies {
    api(project(":keyext.api"))
    api(libs.lsp4j.jsonrpc)
    api(libs.lsp4j.lsp)
    implementation(libs.lsp4j.websocket.jakarta)
    implementation(libs.jetty.websocket.javax.server)
    implementation(libs.picocli)
    implementation(libs.guava)
    implementation("com.google.auto.service:auto-service:1.0-rc5")
    implementation(libs.clickt)

    annotationProcessor(libs.therapi.runtime.javadoc.scribe)
    api(libs.therapi.runtime.javadoc)

    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation(kotlin("test"))
}

tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.add("-parameters") // for having parameter name in reflection
}
repositories {
    mavenCentral()
}