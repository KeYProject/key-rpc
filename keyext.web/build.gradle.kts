plugins {
    id("buildlogic.java-library-conventions")
    application
    alias(libs.plugins.shadow)
}

description = "A web interface for KeY"

application {
    mainClass.set("org.key_project.key.webui.WebUi")
}

dependencies {
    implementation(project(":keyext.api"))
    implementation(libs.jetty.server)
    implementation(libs.jetty.ee11.websocket.jakarta.server)
}