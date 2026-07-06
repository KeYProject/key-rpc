import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    kotlin("jvm")
    id("buildlogic.java-library-conventions")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    api(project(":jmlparser-core"))
    testImplementation(libs.findBundle("testing").get())
    testRuntimeOnly(libs.findBundle("testing-runtime").get())
}

kotlin {
    jvmToolchain(21)
}

tasks.named<Test>("test") {
    useJUnitPlatform()

    maxHeapSize = "1G"

    testLogging {
        events("passed")
    }
}

configure<SpotlessExtension> {
    kotlin {
        target("src/**/*.kt")
        ktlint().setEditorConfigPath("$rootDir/.editorconfig")
        trimTrailingWhitespace()
        endWithNewline()
        licenseHeaderFile("$rootDir/gradle/header", "(package|import|//)")
    }

    kotlinGradle {
        target("*.gradle.kts", "buildSrc/**/*.gradle.kts")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
}