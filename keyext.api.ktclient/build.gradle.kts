plugins {
    id("com.diffplug.spotless")
    id("org.jetbrains.dokka")
    id("org.jetbrains.kotlin.multiplatform")
    kotlin("plugin.serialization") version "2.4.0"
}

repositories{mavenCentral()}

kotlin {
    jvm("desktop")
    js() {
        browser()
        nodejs()
    }

    // --- Source Sets ---
    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
            //implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.coroutines.core)
        }

        jsMain.dependencies {
            implementation(libs.kotlinx.coroutines.js)
        }

        /*
        androidMain.dependencies {
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        */
        val desktopMain by getting {
            dependencies {
                //implementation(libs.ktor.client.cio)
            }
        }
    }
}

//sourceSets.main.get().kotlin.srcDir("src/gen/kotlin")