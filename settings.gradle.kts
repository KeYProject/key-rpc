plugins {
    // Apply the foojay-resolver plugin to allow automatic download of JDKs
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "key-rpc"
include("keyext.api.app", "keyext.api", "keyext.api.doc")
