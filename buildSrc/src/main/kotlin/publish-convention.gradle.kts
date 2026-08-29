import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar

plugins {
    id("com.vanniktech.maven.publish")
    signing
}


publishing {
    repositories {
        maven {
            name = "LOCAL"
            url = uri("$rootDir/local")
        }
        maven {
            // deployment to git.key-project.org/key-public/key
            name = "KEYLAB"
            url = uri("https://git.key-project.org/api/v4/projects/35/packages/maven")
            credentials(HttpHeaderCredentials::class) {
                val userToken = envOrPropertyValue("GITLAB_USER_TOKEN")
                val deployToken = envOrPropertyValue("GITLAB_DEPLOY_TOKEN")
                val ciToken = envOrPropertyValue("GITLAB_CIJOB_TOKEN")

                if (userToken != "") {
                    name = "Private-Token"
                    value = userToken
                } else if (deployToken != "") {
                    name = "Deploy-Token"
                    value = deployToken
                } else {
                    name = "Job-Token"
                    value = ciToken
                }
            }
            authentication {
                create("basic", HttpHeaderAuthentication::class)
            }
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    //signAllPublications()

    configure(
        JavaLibrary(
            javadocJar = JavadocJar.Javadoc(),
            sourcesJar = SourcesJar.Sources(),
        ),
    )

    pom {
        licenses {
            license {
                name = "The GNU General Public License 2"
                url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "weigl"
                email = "weigl@kit.edu"
                name = "Alexander Weigl"
                organization = "Karlsruhe Institute of Technology"
                organizationUrl = "https://kikit.kit.edu"
                roles = listOf("developer", "maintainer")
            }
        }
        scm {
            connection = "scm:git:git://github.com/keyproject/key-rpc.git"
            developerConnection = "scm:git:ssh://github.com/keyproject/key-rpc.git"
            url = "http://keyproject.github.io/key-rpc"
        }
    }
}

fun envOrPropertyValue(key: String): String =
    if (key in System.getenv()) {
        System.getenv(key)
    } else {
        project.findProperty(key)?.toString() ?: ""
    }

val emptyJavadocJar = tasks.findByName("plainJavadocJar")!!
val meta = tasks.findByName("generateMetadataFileForMavenPublication")!!
meta.dependsOn(emptyJavadocJar)

signing { useGpgCmd() }