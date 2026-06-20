plugins {
    java
    id("com.diffplug.spotless")
    id("org.jetbrains.kotlin.jvm")
}

repositories {
    mavenCentral()
    maven { url = uri("https://central.sonatype.com/repository/maven-snapshots/") }
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

spotless {
    java {
        targetExclude("build/**")
        toggleOffOn()
        removeUnusedImports()
        eclipse().configFile("$rootDir/gradle/keyCodeStyle.xml")
        trimTrailingWhitespace()
        importOrder("java|javax", "de.uka", "org.key_project", "", "\\#")
        licenseHeaderFile("$rootDir/gradle/header", "(package|import|//)")
    }

    kotlin {
        target("src/**/*.kt")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        target("*.gradle.kts", "buildSrc/**/*.gradle.kts")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
