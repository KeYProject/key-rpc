pluginManagement {
    plugins {
        kotlin("kapt") version "2.4.0"
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "key-rpc"
include(
    "keyext.api.app",
    "keyext.api", "keyext.api.doc", "keyext.api.client", "keyext.lsp",
    "keyext.api.data"
)
